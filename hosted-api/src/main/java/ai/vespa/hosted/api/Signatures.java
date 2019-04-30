package ai.vespa.hosted.api;

import com.yahoo.security.KeyUtils;

import javax.crypto.Cipher;
import java.io.InputStream;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.Callable;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Signatures {

    /** Returns the data encrypted with the given key. */
    public static byte[] encrypted(byte[] data, Key key) {
        try {
            Cipher rsaEncrypter = Cipher.getInstance("RSA");
            rsaEncrypter.init(Cipher.ENCRYPT_MODE, key);
            return rsaEncrypter.doFinal(data);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /** Returns the data decrypted with the given key. */
    public static byte[] decrypted(byte[] data, Key key) {
        try {
            Cipher rsaDecrypter = Cipher.getInstance("RSA");
            rsaDecrypter.init(Cipher.DECRYPT_MODE, key);
            return rsaDecrypter.doFinal(data);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(e);
        }
    }

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

    /** Reads and Base64-decodes the key data from the given PEM formatted key data. */
    private static byte[] readKey(String pemKey, String start, String end) {
        int keyStart = pemKey.indexOf(start) + start.length();
        int keyEnd = pemKey.indexOf(end);
        if (keyEnd < keyStart)
            throw new IllegalArgumentException("No key found on the form:\n" + start + "<key data>\n" + end);

        return Base64.getMimeDecoder().decode(pemKey.substring(keyStart, keyEnd).getBytes());
    }

}
