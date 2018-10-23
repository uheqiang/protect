package com.ibm.pross.server.dkgnew;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.ibm.pross.common.CommonConfiguration;
import com.ibm.pross.common.DerivationResult;
import com.ibm.pross.common.util.crypto.EcKeyGeneration;
import com.ibm.pross.common.util.crypto.ecc.EcCurve;
import com.ibm.pross.common.util.crypto.ecc.EcPoint;
import com.ibm.pross.common.util.shamir.Polynomials;
import com.ibm.pross.common.util.shamir.Shamir;
import com.ibm.pross.common.util.shamir.ShamirShare;
import com.ibm.pross.server.messages.EciesEncryption;
import com.ibm.pross.server.messages.EncryptedPayload;
import com.ibm.pross.server.messages.Message;
import com.ibm.pross.server.messages.Payload;
import com.ibm.pross.server.messages.Payload.OpCode;
import com.ibm.pross.server.messages.PublicMessage;
import com.ibm.pross.server.messages.SemiPrivateMessage;

class DkgNewShareholder {

	// Group Constants
	public static final EcCurve curve = CommonConfiguration.CURVE;
	public static final EcPoint g = curve.getG();
	public static final EcPoint h = curve.getPointHasher()
			.hashToCurve("nothing up my sleeve".getBytes(StandardCharsets.UTF_8));

	// The set of peer shareholders (we need them for their public encryption keys)
	private final List<DkgNewShareholder> shareholders;

	// Channel related variables
	private final FifoAtomicBroadcastChannel channel;
	private final AtomicInteger currentMessageId = new AtomicInteger(0);

	// Our message processing thread
	private final Thread messageProcessingThread;
	private final AtomicBoolean stopped = new AtomicBoolean(false);

	// The index of this shareholder (ourself) (zero is the base index)
	// This shareholder will hold the share at f(index + 1)
	private final int index;

	// Our own key pair for receiving encrypted values
	private final KeyPair encryptionKeyPair;

	// The number of shareholders
	private final int n;

	// The recovery threshold of the secret
	private final int k;

	// The maximum number of failures
	private final int f;

	// Polynomials f and f'
	private final BigInteger[] ourPolynomial1;
	private final BigInteger[] ourPolynomial2;

	// Shares s and s' created from our polynomials
	private final ShamirShare[] ourShareContributions1;
	private final ShamirShare[] ourShareContributions2;

	// Pedersen commitments: C
	private final EcPoint[] ourCommitments;

	// Received commitments
	protected final EcPoint[][] receivedCommitments;

	// Received share contributions
	protected final ShamirShare[] receivedShareContributions1;
	protected final ShamirShare[] receivedShareContributions2;

	// Our verification vector
	protected final Boolean[] ourVerificationValues;
	private final AtomicInteger successCount = new AtomicInteger(0);

	// Received verification vectors
	protected final Boolean[][] receivedVerifications;

	// Qualified shareholders
	private volatile SortedSet<Integer> qualSet;
	private volatile boolean isQualSetDefined = false;

	// Constructed Shares (x_i)
	private volatile ShamirShare share1;
	private volatile ShamirShare share2;

	// Public Key Contributions y_i = g^a_0
	private final EcPoint[] receivedPublicKeyContributions;
	private final SortedSet<Integer> proofSet = new TreeSet<>();
	private final SortedSet<Integer> rSet = new TreeSet<>();
	protected final EcPoint[][] receivedProvenGs;
	private final SortedSet<Integer> uSet = new TreeSet<>();
	// private final EcPoint[] sharePublicKeys;
	private volatile EcPoint secretPublicKey;

	public DkgNewShareholder(final List<DkgNewShareholder> shareholders, final FifoAtomicBroadcastChannel channel,
			final int index, final int n, final int k, final int f, final boolean sendValidCommitments) {

		verifyConstraints(n, k, f);

		/** Values unique to ourselves **/
		this.index = index;
		this.encryptionKeyPair = EcKeyGeneration.generateKeyPair();

		/** Public shared configuration parameters **/
		this.shareholders = shareholders;
		this.channel = channel;
		this.n = n;
		this.k = k;
		this.f = f;

		/** Values generated internally for DKG-NEW protocol **/

		// The secret is held in the first element of the array: polynomial[0]
		this.ourPolynomial1 = Shamir.generateCoefficients(k);
		this.ourPolynomial2 = Shamir.generateCoefficients(k);

		// Compute shares for i = 1 to N
		this.ourShareContributions1 = Shamir.generateShares(ourPolynomial1, n);
		this.ourShareContributions2 = Shamir.generateShares(ourPolynomial2, n);

		// Create Pedersen commitment: C_ik
		final EcPoint[] gA = Shamir.generateFeldmanValues(ourPolynomial1, g);
		final EcPoint[] hB = Shamir.generateFeldmanValues(ourPolynomial2, h);
		this.ourCommitments = new EcPoint[k];
		for (int i = 0; i < k; i++) {
			if (sendValidCommitments) {
				ourCommitments[i] = curve.addPoints(gA[i], hB[i]);
			} else {
				// Mimic a badly behaving server
				ourCommitments[i] = gA[i];
			}
		}

		/** Variables to track progression of verification and qual set **/

		this.ourVerificationValues = new Boolean[n]; // filled with "null" by default

		/** Variables to track received values **/

		this.receivedCommitments = new EcPoint[n][];
		this.receivedVerifications = new Boolean[n][];
		this.receivedShareContributions1 = new ShamirShare[n];
		this.receivedShareContributions2 = new ShamirShare[n];
		this.receivedPublicKeyContributions = new EcPoint[n];
		this.receivedProvenGs = new EcPoint[n][n];
		// this.sharePublicKeys = new EcPoint[n];

		// Start the shareholder (await and process messages)
		this.messageProcessingThread = startMainLoop();
		this.messageProcessingThread.start();
	}

	private static void verifyConstraints(final int n, final int k, final int f) {
		if (!(f < k)) {
			throw new IllegalArgumentException("F must be less than K");
		}

		if (!(k <= (n - 2 * f))) {
			throw new IllegalArgumentException("K must be less than or equal to N - 2F");
		}
	}

	public Thread startMainLoop() {

		return new Thread(new Runnable() {

			@Override
			public void run() {
				while (DkgNewShareholder.this.stopped.get() == false) {
					try {
						synchronized (DkgNewShareholder.this.channel) {
							DkgNewShareholder.this.channel.wait(1000);
						}
					} catch (InterruptedException e) {
						// Ignore
					}
					while (DkgNewShareholder.this.channel.getMessageSize() > DkgNewShareholder.this.currentMessageId
							.get()) {
						messageIsAvailable();
					}
				}
			}
		}, "Shareholder-Thread-" + this.index);
	}

	/**
	 * A message is available on the queue, get it and deliver it for processing
	 */
	private synchronized void messageIsAvailable() {
		int messageId = this.currentMessageId.getAndIncrement();
		final Message message = this.channel.getMessage(messageId);
		deliver(message);
	}

	/**
	 * Deliver a message received on the FIFO-AB channel to the correct method
	 * 
	 * @param message
	 */
	private synchronized void deliver(final Message message) {

		final OpCode opcode;
		if (message instanceof SemiPrivateMessage) {
			opcode = ((SemiPrivateMessage) message).getPublicPayload().getOpcode();
		} else {
			opcode = ((PublicMessage) message).getPayload().getOpcode();
		}

		switch (opcode) {
		case MS:
			deliverShareContributions((SemiPrivateMessage) message);
			break;
		case VV:
			deliverVerificationtMessage((PublicMessage) message);
			break;
		case RB:
			deliverRebuttalMessage((PublicMessage) message);
			break;
		case ZK:
			deliverProofMessage((PublicMessage) message);
			break;
		case BP:
			deliverBulkProofsMessage((PublicMessage) message);
			break;
		default:
			break;
		}
	}

	public void stop() {

		this.stopped.set(true);

		// Wake the sleeping threads
		synchronized (this.channel) {
			this.channel.notifyAll();
		}

		try {
			this.messageProcessingThread.join();
		} catch (InterruptedException e) {
			// Interrupted
		}
	}

	/**
	 * Send out initial message containing our share contributions (privately
	 * encrypted to each peer shareholder) and our public Pedersen commitments. This
	 * will start the DKG-NEW protocol, and it will be driven to completion.
	 */
	public void broadcastShareContribtions() {

		// Create map for storing encrypted payloads
		final NavigableMap<Integer, EncryptedPayload> encryptedPayloads = new TreeMap<>();

		for (int recipientIndex = 0; recipientIndex < this.shareholders.size(); recipientIndex++) {

			// Create an encrypted payload from this private payload
			final Payload privatePayload = new Shares(this.ourShareContributions1[recipientIndex],
					this.ourShareContributions2[recipientIndex]);

			// Use recipient public key to encrypt each private payload
			final PublicKey recipientPublicKey = this.shareholders.get(recipientIndex).getEncryptionPublicKey();
			final EncryptedPayload encryptedPayload = EciesEncryption.encrypt(privatePayload, recipientPublicKey);

			// Store it in the map
			encryptedPayloads.put(recipientIndex, encryptedPayload);
		}

		// Create a semi-private message
		this.channel.broadcast(
				new SemiPrivateMessage(this.index, new PedersonCommitments(this.ourCommitments), encryptedPayloads));
	}

	/**
	 * Process Share Contributions and update verification vector
	 * 
	 * @param message
	 */
	protected synchronized void deliverShareContributions(final SemiPrivateMessage message) {

		final int senderIndex = message.getSenderIndex();
		if (this.ourVerificationValues[senderIndex] != null) {
			// We have already received a message for this sender
			return;
		}

		// Extract the public and private portions of the payloads
		final EncryptedPayload encryptedContent = ((SemiPrivateMessage) message).getEncryptedPayload(this.index);
		final Payload privatePayload = EciesEncryption.decryptPayload(encryptedContent,
				this.encryptionKeyPair.getPrivate());
		final Payload publicPayload = ((SemiPrivateMessage) message).getPublicPayload();

		// Get parameters from message
		final ShamirShare share1 = ((Shares) privatePayload).share1;
		final ShamirShare share2 = ((Shares) privatePayload).share2;
		final EcPoint[] commitments = ((PedersonCommitments) publicPayload).getCommitments();
		this.receivedCommitments[senderIndex] = commitments;

		this.ourVerificationValues[senderIndex] = verifyShareConsistency(this.index, share1, share2, commitments);
		if (this.ourVerificationValues[message.getSenderIndex()]) {

			// Store the share
			this.receivedShareContributions1[senderIndex] = share1;
			this.receivedShareContributions2[senderIndex] = share2;

			// See if we have reached a threshold to proceed to next phase
			if (successCount.incrementAndGet() == (this.n - this.f)) {
				broadcastVerificationVector();
			}

		} else {
			// Someone sent us something incorrect, they will be reported
		}
	}
	
	/**
	 * Verify that a contribution to our share is consistent with the published
	 * Pedersen commitments
	 * 
	 * @param recipientIndex
	 * @param share1
	 * @param share2
	 * @param commitment
	 * @return
	 */
	private boolean verifyShareConsistency(final int recipientIndex, final ShamirShare share1, final ShamirShare share2,
			final EcPoint[] commitment) {

		// Expected value (g^s * h^s')
		final EcPoint Gs1 = curve.multiply(g, share1.getY());
		final EcPoint Hs2 = curve.multiply(h, share2.getY());
		final EcPoint expected = curve.addPoints(Gs1, Hs2);

		// Verify consistency against public commitment
		final BigInteger j = BigInteger.valueOf(recipientIndex + 1);
		EcPoint sum = EcPoint.pointAtInfinity;
		for (int i = 0; i < this.k; i++) {
			final EcPoint term = curve.multiply(commitment[i], j.pow(i));
			sum = curve.addPoints(sum, term);
		}

		return expected.equals(sum);
	}

	/**
	 * Create and broadcast verfication vector to all other shareholders
	 */
	private void broadcastVerificationVector() {
		final Verification payload = new Verification(this.ourVerificationValues);
		final PublicMessage message = new PublicMessage(this.index, payload);
		this.channel.broadcast(message);
	}

	/**
	 * Process another shareholder's report of their verification vector and work to
	 * build the QUAL set
	 * 
	 * @param message
	 */
	protected synchronized void deliverVerificationtMessage(final PublicMessage message) {

		final int senderIndex = message.getSenderIndex();

		if (this.receivedVerifications[senderIndex] == null) {
			// Save received verification
			final Boolean[] receivedVerificationVector = ((Verification) message.getPayload()).getVerificationVector();
			this.receivedVerifications[senderIndex] = receivedVerificationVector;

			// Check if we have received an accusation
			if ((receivedVerificationVector[this.index] != null) && (receivedVerificationVector[this.index] == false)) {

				// Send a rebuttal
				final Rebuttal rebuttal = new Rebuttal(senderIndex, this.ourShareContributions1[senderIndex],
						this.ourShareContributions2[senderIndex]);
				final PublicMessage rebuttalMessage = new PublicMessage(this.index, rebuttal);
				this.channel.broadcast(rebuttalMessage);
			}

			buildQualSet();

		} else {
			// Ignore duplicate messages from same sender
		}
	}

	/**
	 * Process a rebuttal
	 * 
	 * @param message
	 */
	protected synchronized void deliverRebuttalMessage(final PublicMessage message) {

		// The accused sent the rebuttal
		final int accusedIndex = message.getSenderIndex();

		// The accuser is indicated in the rebuttal message
		final Rebuttal rebuttal = ((Rebuttal) (((PublicMessage) message).getPayload()));
		final int accuserIndex = rebuttal.getAccuserIndex();

		if (this.receivedVerifications[accuserIndex] != null) {

			// Load received verification
			final Boolean[] receivedVerificationVector = this.receivedVerifications[accuserIndex];

			if (this.receivedCommitments[accusedIndex] != null) {

				// Loaded received commitments
				final EcPoint[] commitments = this.receivedCommitments[accusedIndex];

				final ShamirShare share1 = rebuttal.getShare1();
				final ShamirShare share2 = rebuttal.getShare2();

				// Test if the received share is valid
				if (verifyShareConsistency(accuserIndex, share1, share2, commitments)) {
					// If we made accusation, and it is good, use the new result
					if (accuserIndex == this.index) {
						this.receivedShareContributions1[accusedIndex] = share1;
						this.receivedShareContributions2[accusedIndex] = share2;
					}

					// Also, update the table of received verifications
					receivedVerificationVector[accusedIndex] = true;

					buildQualSet();

				} else {
					// Rebuttal is not valid, ignore it
				}
			}
		}
	}

	/**
	 * Monotonically build up the qual set.
	 */
	private synchronized void buildQualSet() {

		if (isQualSetDefined) {
			return;
		}

		final SortedSet<Integer> qual = new TreeSet<>();

		for (int j = 0; j < n; j++) {
			int successes = 0;
			for (int i = 0; i < n; i++) {
				if ((this.receivedVerifications[i] != null) && (this.receivedVerifications[i][j] != null)
						&& (this.receivedVerifications[i][j].equals(Boolean.TRUE))) {
					successes++;
				}
			}

			if (successes > (this.k + this.f)) {
				qual.add(j);
			}

			// Check if QUAL is complete
			if (qual.size() == (this.f + 1)) {

				this.qualSet = Collections.unmodifiableSortedSet(qual);

				// We have determined qual, build a share:
				BigInteger share1Y = BigInteger.ZERO;
				BigInteger share2Y = BigInteger.ZERO;

				for (final Integer index : this.qualSet) {
					share1Y = share1Y.add(this.receivedShareContributions1[index].getY()).mod(curve.getR());
					share2Y = share2Y.add(this.receivedShareContributions2[index].getY()).mod(curve.getR());
				}

				this.share1 = new ShamirShare(BigInteger.valueOf(this.index + 1), share1Y);
				this.share2 = new ShamirShare(BigInteger.valueOf(this.index + 1), share2Y);

				// Broadcast ZKP if we are in QUAL
				if (qual.contains(this.index)) {
					broadcastZkp();
				}

				isQualSetDefined = true;
				break;
			}
		}
	}

	/**
	 * Broadcast a ZKP of our ai_0 This method is only called by members of QUAL
	 */
	private void broadcastZkp() {

		final ZeroKnowledgeProof proof = ZeroKnowledgeProver.createProof(this.ourPolynomial1[0],
				this.ourPolynomial2[0]);

		final ZkpPayload payload = new ZkpPayload(proof);
		final PublicMessage message = new PublicMessage(this.index, payload);
		this.channel.broadcast(message);
	}

	/**
	 * Process a proof sent by a member of qual. This will be used to determine the
	 * public key y = g^x
	 * 
	 * @param message
	 */
	protected synchronized void deliverProofMessage(final PublicMessage message) {

		// TODO: For all the non-expected cases, go into an "alert" method to flag the
		// unexpected/erroneous behavior. E.g. someone sent message at wrong time, or
		// who shouldn't have, or sent a wrong value.

		// Get the sender index
		final int senderIndex = message.getSenderIndex();

		if (this.qualSet.contains(senderIndex)) {

			// The accuser is indicated in the rebuttal message
			final ZkpPayload zkp = ((ZkpPayload) (((PublicMessage) message).getPayload()));

			if (this.receivedCommitments[senderIndex] != null) {
				if (this.receivedPublicKeyContributions[senderIndex] == null) {

					// Verify the proof
					final ZeroKnowledgeProof proof = zkp.getProof();
					if (ZeroKnowledgeProver.verifyProof(this.receivedCommitments[senderIndex][0], proof)) {
						// Proof is Good! Save the result.
						this.receivedPublicKeyContributions[senderIndex] = proof.getA0();
						this.proofSet.add(senderIndex);

						// If we have built up a complete R set, broadcast our bulk proof of values
						// received by members of R
						if (this.proofSet.size() == (this.qualSet.size() - this.f)) {
							// Calculate R = QualSet \ ProofSet
							final SortedSet<Integer> R = new TreeSet<>(this.qualSet);
							R.removeAll(this.proofSet);
							rSet.addAll(R);
							broadcastBulkProofs();
						}

					} else {
						// Proof failed
					}
				}
			}
		}
	}

	/**
	 * Send our proof for received share contributions from all members of R
	 */
	private void broadcastBulkProofs() {

		// Generate proofs of knowledge for each in R
		SortedMap<Integer, ZeroKnowledgeProof> proofs = new TreeMap<>();
		for (final Integer i : this.rSet) {
			final BigInteger s1 = this.receivedShareContributions1[i].getY();
			final BigInteger s2 = this.receivedShareContributions2[i].getY();

			proofs.put(i, ZeroKnowledgeProver.createProof(s1, s2));
		}

		final ZkpBulkPayload payload = new ZkpBulkPayload(proofs);
		final PublicMessage message = new PublicMessage(this.index, payload);
		this.channel.broadcast(message);
	}

	/**
	 * Process a bulk proof message
	 * 
	 * @param message
	 */
	protected synchronized void deliverBulkProofsMessage(final PublicMessage message) {

		// Get the sender index
		final int j = message.getSenderIndex();

		// Ensure we received a valid set of commitments from this sender
		if (this.receivedCommitments[j] != null) {

			// The accuser is indicated in the rebuttal message
			final ZkpBulkPayload bulkProofs = ((ZkpBulkPayload) (((PublicMessage) message).getPayload()));

			SortedMap<Integer, ZeroKnowledgeProof> proofs = bulkProofs.getProofs();

			// Ensure their set of R matches ours
			if (proofs.keySet().equals(this.rSet)) {

				// Verify each proof
				for (final Entry<Integer, ZeroKnowledgeProof> entry : proofs.entrySet()) {

					final int i = entry.getKey();
					final ZeroKnowledgeProof proof = entry.getValue();

					// Expected public key from committed feldman values
					final EcPoint Dji = Shamir.computeSharePublicKey(this.receivedCommitments[i], j + 1);

					// Verify proof
					if (!ZeroKnowledgeProver.verifyProof(Dji, proof)) {
						return; // Ignore this shareholder, his proof doesn't check out
					}
				}

				// Store proofs
				for (final Entry<Integer, ZeroKnowledgeProof> entry : proofs.entrySet()) {
					final int i = entry.getKey();
					final ZeroKnowledgeProof proof = entry.getValue();
					this.receivedProvenGs[i][j] = proof.getA0();
				}

				// Add sender to set j
				uSet.add(j);

				// Once there is a reconstruction threshold, we can solve for the public key
				if (uSet.size() == this.k) {
					solvePublicKey();
				}
			}

			// Attempt key recovery
		}
	}

	/**
	 * Determine the overall Public Key associated with the distributed secret "x",
	 * where y = g^x This is done by interpolating each of the values y_i = g^x_i,
	 * and then summing the g^x_i for all i in Qual
	 */
	private void solvePublicKey() {

		// Use interpolation of the K published values in set U to recover the public
		// keys.
		for (final Integer i : this.rSet) {
			final List<DerivationResult> shareholderContributions = new ArrayList<>();
			for (final int k : this.uSet) {
				final EcPoint Gs = this.receivedProvenGs[i][k];
				final DerivationResult result = new DerivationResult(BigInteger.valueOf(k + 1), Gs);
				shareholderContributions.add(result);
			}

			// Derive the public key contribution (helps if never received)
			final EcPoint publicKeyContribution = Polynomials.interpolateExponents(shareholderContributions, this.k, 0);
			this.receivedPublicKeyContributions[i] = publicKeyContribution;
		}

		// We can now interpolate the total public key for the secret
		EcPoint totalPublicKey = EcPoint.pointAtInfinity;
		for (final Integer i : this.qualSet) {
			totalPublicKey = curve.addPoints(totalPublicKey, this.receivedPublicKeyContributions[i]);
		}

		this.secretPublicKey = totalPublicKey;
	}

	/**
	 * Returns the public encryption key for this shareholder which can be used by
	 * others to send confidential messages to this shareholder
	 * 
	 * @return
	 */
	public PublicKey getEncryptionPublicKey() {
		return this.encryptionKeyPair.getPublic();
	}

	/**
	 * Returns the public key of the secret: y = g^x
	 * 
	 * @return
	 */
	public EcPoint getSecretPublicKey() {
		return this.secretPublicKey;
	}

	/**
	 * Returns the public key of the share for this shareholder: y_i = g^x_i
	 * 
	 * @return
	 */
	public EcPoint getSharePublicKey() {
		// Sanity check
		return curve.multiply(g, this.share1.getY());
		// return getSharePublicKey(this.index);
	}

	public EcPoint getSharePublicKey(final int index) {
		throw new RuntimeException("Not yet implemented");
		// return this.sharePublicKeys[index];
	}

	/**
	 * Return the set of shareholders who have contributed to the secret x
	 * 
	 * (Only used in tests)
	 * 
	 * @return
	 */
	protected SortedSet<Integer> getQualSet() {
		return this.qualSet;
	}

	/**
	 * Return the secret share of this shareholder for g^s
	 * 
	 * (Only used in tests)
	 * 
	 * @return
	 */
	protected ShamirShare getShare1() {
		return share1;
	}

	/**
	 * Return the secret share of this shareholder for h^s
	 * 
	 * (Only used in tests)
	 * 
	 * @return
	 */
	protected ShamirShare getShare2() {
		return share2;
	}

	/**
	 * Wait until this shareholder has established the set of qualified shareholders
	 */
	public void waitForQual() {
		while (this.isQualSetDefined == false) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				// Ignored
			}
		}
	}

	/**
	 * Wait until this shareholder has constructed the public key: y = g^x
	 */
	public void waitForPublicKeys() {
		while (this.secretPublicKey == null) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				// Ignored
			}
		}
	}

}