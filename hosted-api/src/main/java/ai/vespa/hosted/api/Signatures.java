package ai.vespa.hosted.api;

import com.yahoo.security.KeyUtils;

import java.io.InputStream;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.concurrent.Callable;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Signatures {

    /** Returns the SHA-256 hash of the content in the implied input stream, consuming it in the process. */
    public static byte[] sha256Digest(Callable<InputStream> in) {
        try (DigestInputStream digester = sha256Digester(in.call())) {
            byte[] buffer = new byte[1 << 10];
            while (digester.read(buffer) != -1); // Consume the stream to compute the digest.

            return digester.getMessageDigest().digest();
        }
        catch (IllegalStateException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /** Wraps the given input stream in a digester which computes a SHA 256 hash of the contents read through it. */
    public static DigestInputStream sha256Digester(InputStream in) {
        try {
            return new DigestInputStream(in, MessageDigest.getInstance("SHA-256"));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Returns a canonical representation of the given request data. */
    public static byte[] canonicalMessageOf(String method, URI requestUri, String timestamp, String hash) {
        return (method + "\n" + requestUri.normalize() + "\n" + timestamp + "\n" + hash).getBytes(UTF_8);
    }

}
