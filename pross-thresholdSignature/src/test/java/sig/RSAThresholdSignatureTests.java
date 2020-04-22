package sig;

import com.ibm.pross.common.util.crypto.rsa.threshold.sign.client.RsaDealingClient;
import com.ibm.pross.common.util.crypto.rsa.threshold.sign.exceptions.BadArgumentException;
import com.ibm.pross.common.util.crypto.rsa.threshold.sign.exceptions.BelowThresholdException;
import com.ibm.pross.common.util.crypto.rsa.threshold.sign.server.RsaSignatureServer;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

/**
 * @author heqiang
 * @date 2020/4/22 10:50
 */
public class RSAThresholdSignatureTests {

    public static void main(String[] args) throws NoSuchPaddingException, InvalidKeyException, BelowThresholdException,
            IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, BadArgumentException, InvalidKeySpecException {
        System.out.println("Dealing secret to the servers...");

        // Create servers
        final int serverCount = 18;
        final int threshold = 10;
        RsaSignatureServer[] servers = RsaSignatureServer.initializeServers(serverCount);

        // Setup dealer
        RsaDealingClient dealer = new RsaDealingClient(servers, threshold);

        // Register user a secret to be held in trust of the servers
        final String keyName = "joe";
        final byte[] toBeSigned = "my message".getBytes(StandardCharsets.UTF_8);
        byte[] testSignature = dealer.registerWithServers(keyName, toBeSigned);


    }
}
