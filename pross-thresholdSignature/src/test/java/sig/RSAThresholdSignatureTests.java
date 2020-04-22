package sig;

import com.ibm.pross.common.util.crypto.rsa.threshold.sign.client.RsaDealingClient;
import com.ibm.pross.common.util.crypto.rsa.threshold.sign.client.RsaSignatureClient;
import com.ibm.pross.common.util.crypto.rsa.threshold.sign.exceptions.BadArgumentException;
import com.ibm.pross.common.util.crypto.rsa.threshold.sign.exceptions.BelowThresholdException;
import com.ibm.pross.common.util.crypto.rsa.threshold.sign.server.RsaSignatureServer;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;

/**
 * @author heqiang
 * @date 2020/4/22 10:50
 */
public class RSAThresholdSignatureTests {

    public static void main(String[] args) throws BelowThresholdException, NoSuchAlgorithmException, BadArgumentException {
        System.out.println("Dealing secret to the servers...");

        // Create servers
        final int serverCount = 18;
        final int threshold = 10;
        RsaSignatureServer[] servers = RsaSignatureServer.initializeServers(serverCount);

        // Setup dealer
        RsaDealingClient dealer = new RsaDealingClient(servers, threshold);

        // Register user a secret to be held in trust of the servers
        final String keyName = "joe";
        dealer.shareSecritWithServers(keyName);

        final byte[] toBeSigned = "my message".getBytes(StandardCharsets.UTF_8);
        RsaSignatureClient client = new RsaSignatureClient(servers, threshold);

        // create threshold signature and verify signature
        boolean verify = client.thresholdSignaturesAndVerify(keyName, toBeSigned);
        System.out.println(" verify：" + verify);


    }
}
