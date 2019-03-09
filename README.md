# protect
A **P**latform for **Ro**bust **T**hr**e**shold **C**ryp**t**ography

## Overview

***PROTECT*** provides a platform for [*threshold-secure* cryptography](https://en.wikipedia.org/wiki/Threshold_cryptosystem).  It can be used to implement systems and services that tolerate multiple simultaneous faults and security breaches without loss of privacy, availability, or correctness.  Moreoever, the system self-heals from faults and self-recovers from breaches. These restorative features enable **PROTECT** to maintain confidential elements (e.g., secret keys, private keys, bitcoin wallets, numbered bank accounts) durably over long periods, despite the many inevitable data loss and data exposure events that will occur over the course of that time.

***PROTECT*** and leverages the mathematical relationships that exist between shares of a [secret sharing scheme](https://en.wikipedia.org/wiki/Secret_sharing) for distributed and secure function evaluations. These functions include [distributed key generation](https://en.wikipedia.org/wiki/Distributed_key_generation), [proactive security](https://en.wikipedia.org/wiki/Proactive_secret_sharing), [share recovery](https://en.wikipedia.org/wiki/Proactive_secret_sharing#Motivation), [key derivation](https://en.wikipedia.org/wiki/Key_derivation_function), [public key decryption](https://en.wikipedia.org/wiki/Public-key_cryptography), and [signature generation](https://en.wikipedia.org/wiki/Digital_signature).

***PROTECT*** includes several example client utilities  to show how to build a threshold-secure applications. These example utilities include:
* A distributed Certificate Authority whose private signing key is not stored in any location
* A threshold-secure decryption service whose private decryption key never exists in any location
* A secret storage and retrieval client that allows the secure maintenance of arbitrary values

Using these techniques one can engineer secure cryptographic services *having neither any single point of failure nor any single point of compromise*.

### Functionality

The following section describes all of the funtionality ***PROTECT***.

### Secret Lifecycle

The following actions are performed by servers, although the distributed key generation is initiated by a user.  Proactive Refresh and Share Recovery both occur on a scheduled periodic basis for all existing established secrets.

* **Distributed Key Generation** - Generation of shares of a random values which no one knows
* **Proactive Refresh** - Regenerates new shares for an existing secret, eliminating utility of old shares (which might have been exposed)
* **Share Recovery** - Rebuilding a lost or destroyed share without having to restore the secret or expose any share
  
### Share Management

The following are supported user actions related to the management of shares.  Note that ***PROTECT*** implements fine-grained access controls, permitting different users to be authorized to perform different functions or operations for different secrets.

* **Store Share** - Stores a specified share to enable reliably maintenance of a specific secret
* **Read Share** - Reads a share to enable determination of a secret's value
* **Delete Share** - Deletes a share to allow destruction of a secret
* **Recover Share** - Initiates an immediate share recovery of a deleted share
* **Disable Share** - Temporarily disables a share for usage
* **Enable Share** - Enables a previously disabled share for usage

### Cryptographic Operations

***PROTECT*** supports the following cryptographic functions out-of-the box today:

#### Elliptic Curves
* **Pseudorandom Functions** (PRF) - May be used to derive random looking output deterministically (for PRNGs, or KDFs)
* **Oblivious Pseudorandom Functions** - The same as a PRF but [blinded](https://en.wikipedia.org/wiki/Pseudorandom_function_family#Oblivious_pseudorandom_functions) so as to hide the input (for password hardening, OPAQUE, oblivious KDF)
* **ECIES Encryption** - The EC version of [Integrated Encryption Scheme](https://en.wikipedia.org/wiki/Integrated_Encryption_Scheme) which is based on [ElGamal](https://en.wikipedia.org/wiki/ElGamal_encryption) encryption
* **Elliptic Curve Diffie Hellman Key Agreement** (ECDH) - [ECDH](https://en.wikipedia.org/wiki/Elliptic-curve_Diffie%E2%80%93Hellman) is a Key Agreement Scheme commonly used in [TLS handshakes](https://en.wikipedia.org/wiki/Transport_Layer_Security)

#### RSA
* **Signature Generation** - Threshold signature scheme for [RSA](https://en.wikipedia.org/wiki/RSA_(cryptosystem)) based on Victor Shoup's [Practical Threshold Signatures](https://www.shoup.net/papers/thsig.pdf)
* **Blinded Signature Generation** - The same as above but [blinded](https://en.wikipedia.org/wiki/Blind_signature#Blind_RSA_signatures) from the signer.
* **Decryption** - Decryption of a ciphertext encrypted under an RSA public key. (supported but ***not recommended***, see note below)

### Roadmap Items

Very shortly support will be added to ***PROTECT*** for the following operations:

#### Diffie Hellman over Prime Groups
* Pseudorandom Functions
* Oblivious Pseudorandom Functions
* ElGamal Encryption
* Diffie-Hellman Key Agreement

#### Bilinear Pairing of Elliptic Curves
* [Generic Elliptic Curve Pairing Operation](https://en.wikipedia.org/wiki/Pairing-based_cryptography)
* [Boneh–Lynn–Shacham Signatures](https://en.wikipedia.org/wiki/Boneh%E2%80%93Lynn%E2%80%93Shacham)
* Partially Oblivious Pseudorandom Functions - As in the [Pythia PRF Service](https://eprint.iacr.org/2015/644)

### Project Vision

Over a longer time horizion the ***PROTECT*** project aims to support:

#### More Signature Schemes
* Schnorr Signatures (possibly leveraging Share Conversion)
* ECDSA Signatures

#### Multiparty Computation
* Share Addition
* Share Multiplication
* Threshold AES

#### RSA Extensions
* RSA Distributed Key Generation
* RSA Proactive Refresh
* RSA Share Recovery

#### Post-Quantum Cryptography

## Deploying Protect

Protect is easy to deploy, and can get up and running in as few as three commands:

```bash
$ git clone https://github.com/jasonkresch/protect.git
$ ./protect/build.sh
$ ./protect/bin/launch-all-servers.sh 5
```
However this will launch protect using default configuration parameters, with default (***not secure***) keys, and running all instances on a single machine (***not reliable***).  The following subsections provide details on how to deploy ***PROTECT*** in a secure in reliable manner.

### Download

[![Alt text](https://img.youtube.com/vi/9sDgPOUpADw/0.jpg)](https://www.youtube.com/watch?v=9sDgPOUpADw)

### Build

[![Alt text](https://img.youtube.com/vi/Cz9VV0FzW10/0.jpg)](https://www.youtube.com/watch?v=Cz9VV0FzW10)

#### Prerequisites

**PROTECT** is written in Java but also includes some examples that use python.  On a fresh Ubuntu 18.04 system the following packages are required in order to build and launch the product.

```bash
$ sudo apt-get-update
$ sudo apt-get install openjdk-8-jdk-headless
$ sudo apt install maven
$ sudo apt install python
```

#### Compiling

Once the above prerequisites are installed PROTECT may be built by invoking the `build.sh` script.

```bash
$ git clone https://github.com/jasonkresch/protect.git
$ cd protect
$ ./build.sh
```
The end result of the build script is a self-contained jar file: `pross-server/target/pross-server-1.0-SNAPSHOT-shaded.jar`

### Configuration

#### Keys and Certificates

Pre-instaled, can skip this step if just testing, but any real deployment ***MUST*** complete this step, to create new certificates for each client and server.  Note: security of client CA certificate not important, servers use direct public key matching.  However, most browsers require the server to present the client CA certificate to prompt the user to provide one. This is not an issue for command line interaction via cURL.

#### Servers

[![Alt text](https://img.youtube.com/vi/BHM17XE6ZhQ/0.jpg)](https://www.youtube.com/watch?v=BHM17XE6ZhQ)

Show sample configuration file.
Describe optional and required fields.
Servers beyond the num servers are ignored.
Only need to change n, and set the sever IP addresses.

#### Client Access Controls

[![Alt text](https://img.youtube.com/vi/DXvrh1b8GH4/0.jpg)](https://www.youtube.com/watch?v=DXvrh1b8GH4)

Supports fine-grained user access conrols.
Uses client-side certificate authentication over TLS
Debug authentication by going to (show URL of id check page).
Describe each permission, meaning.

### Launching Servers

[![Alt text](https://img.youtube.com/vi/H4rX8gtqjrI/0.jpg)](https://www.youtube.com/watch?v=H4rX8gtqjrI)

Unqique server ID, all need to start for service to begin.

## Operations

### Interacting with System

Servers listen over HTTPS, on ports 8081 - 808n where n is number of servers.  Each server id.

#### Browser Interaction

Exploring system, servers, secrets, shares. (With read permission)
Configuring CA certificates (avoid SSL error).
Note: each server uses its own CA to issue its certificates.  These may be generated individually at each sever, then collected and distributed to all.  CA itself not checked, only used for client browsers. Servers' use direct Public key matching.

#### Command Line Interction

Initiating a DKG
Getting share info
Deleting a share
Performing Exponentiation (Getting json)
Performing signature generation (getting json)

### Secret Management

#### Generating a random secret

#### Storing a specific secret

#### Storing an RSA Private Key

#### Reading a stored secret

### Cryptograpic Operations

#### ECIES Decryption

#### Certificate Issuance



3. Server Configuration




6. Managing Secrets
[![Alt text](https://img.youtube.com/vi/ZMjMlC52MJc/0.jpg)](https://www.youtube.com/watch?v=ZMjMlC52MJc)

7. Cryptographic Operations
[![Alt text](https://img.youtube.com/vi/hVjxZmUPwlU/0.jpg)](https://www.youtube.com/watch?v=hVjxZmUPwlU)

## System Architecture

Describe system architecture
How Shareholders are connected, how they communicate
Link to Tunable Secrity eprint paper.


## References


This project implements the Proactive Secret Sharing (PROSS) protocol, first described in 1995 by Amir Herzberg, Stanislaw Jarecki, Hugo Krawczyk, and Moti Yung in their paper ["Proactive Secret Sharing Or: How to Cope with Perpetual Leakage"](https://pdfs.semanticscholar.org/d367/55ccc7902e3e09db5c82897401ab0877df3d.pdf).

Additionally, this project implements the Distributed Key Generation (DKG) protocol, first described in 1999 by Rosario Gennaro, Stanislaw Jarecki, Hugo Krawczyk, and Tal Rabin in their 1999 paper ["Secure Distributed Key Generation for Discrete-Log Based Cryptosystems"](https://groups.csail.mit.edu/cis/pubs/stasio/vss.ps.gz).

Both of these protocols depend on an atomic broadcast channel. In the real world of asynchronrouns networks and distributed systems the idealization of an atomic broadcast channel must be built on top of a distributed, byzantine-fault-tolerant, consensus system.  Therefore network communication among the component servers of the PROSS and DKG systems uses [Byzantine Fault Tolerant (BFT) State Machine Replication (SMR)](http://repositorio.ul.pt/bitstream/10451/14170/1/TR-2013-07.pdf) based on the [BFT-SMaRt library](https://github.com/bft-smart/library).

More references:
- Victor Shoup's Practical Threshold RSA Signatures
- BLS Signatures
- Ellipc Curve Pairing
- Blind Signatures (Chaum)
- Other references from NIST submission
- Ford-Kaliski on password hardening
- NIST Draft on Threshold Security
https://www.nongnu.org/libtmcg/dg81_slides.pdf

## Team

The team behind PROTECT includes ...
Involved in the design fo protocols, architecture, algorithms, PVSS, APVSS.


## Contributing
Contributions welcome! See [Contributing](CONTRIBUTING.md) for details.

## Related Projects

- DKG implementation
- Thunderella
- https://en.wikipedia.org/wiki/Vanish_(computer_science)
