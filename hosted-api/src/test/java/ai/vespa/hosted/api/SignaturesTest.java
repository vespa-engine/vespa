package ai.vespa.hosted.api;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.security.DigestInputStream;
import java.security.Key;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static ai.vespa.hosted.api.Signatures.decrypted;
import static ai.vespa.hosted.api.Signatures.encrypted;
import static ai.vespa.hosted.api.Signatures.parsePrivatePemPkcs8RsaKey;
import static ai.vespa.hosted.api.Signatures.parsePublicPemX509RsaKey;
import static ai.vespa.hosted.api.Signatures.sha256Digest;
import static ai.vespa.hosted.api.Signatures.sha256Digester;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests that encryption and decryption are inverses, and that the keys used for this can be loaded.
 *
 * To generate appropriate keys, run the following commands:
 * <ol>
 * <li>{@code openssl genrsa -out mykey.pem 4096}</li>
 * <li>{@code openssl pkcs8 -topk8 -in mykey.pem -outform PEM -out private_key.pem -nocrypt}</li>
 * <li>{@code openssl rsa -pubout -in mykey.pem -outform PEM -out public_key.pem}</li>
 * </ol>
 * Step 1 generates a private key, Step 2 converts this to Java-acceptable PKCS8 format,
 * and Step 3 extracts and writes the public key to a separate file.
 * The key generated in Step 1 should be thrown away when the other steps are complete.
 *
 * @author jonmv
 */
public class SignaturesTest {

    public static final String pemPrivateKey = "-----BEGIN PRIVATE KEY-----\n" +
                                               "MIIJQgIBADANBgkqhkiG9w0BAQEFAASCCSwwggkoAgEAAoICAQCwovyO8gTLal4u\n" +
                                               "0S/KFZZv87/1EdN89eN5dqFeZWtcTjNjkInox/U8WTBDwrxLfliFJs5kzhpiEX1X\n" +
                                               "wXiPDJadfqpZQytZUc65LfZ+SN02jTP8rEJsbmdEhYu6/8+Qc24gmowJvCfpC53l\n" +
                                               "r7DF4uAuqWugK1QkoAaPhiVnmLygx0Kp8cqX8MzBySzlfG0gCvPoZwzbPx84Hc1F\n" +
                                               "7GPfYxQsfvJtaEIPH9FKoZBgnyxYQUgrgdKZirCohCkUCb+LEhKR5YEvqKSCpyFk\n" +
                                               "0x/vHKfInGMIeC2TEA2xCEePaSADMsN4Gy4z79ZGEEZr4tG8Ry67/Nm/37t5WGlp\n" +
                                               "gm94tV1zA97Bb/yWIGYojOP9A8KftmVVGTT3hNkPpMIRHj4kVb2ajS8UPxL2jTOE\n" +
                                               "yVyTMIXhk02DI/20jxZ5yrbkD6xRjn9Zk0uL6aRr8ad8+33sauEzRgdvUzq3ieHV\n" +
                                               "Hjfs3CGtQdH9gNrNsG8uTlPvUaB9B+fjgv1LCyoRhoe91kB9XQhkkG0Oj/IJ7/K+\n" +
                                               "C0dPKYF4GtEykFTmt5w+1bxuARaUeXpDJ27D0QEmM1jG8GmBX8P+00Fxq7RobGaH\n" +
                                               "CkF69616RXpcDaaILuta7ZauraXkuNcSBk4Pk803a9BLYJ1630kdhZtVsreYEuCg\n" +
                                               "KFwLlej9ActThHJ0jSLT5nb+2MawawIDAQABAoICAGv76Ax3dmjo6RUT+3Q+iE5+\n" +
                                               "pE5tDG6rX9pEpNgxhlXS1OW8WiL+AzVWjQQPy88XOYSFOc40lbp4WLlKZKqHFpjH\n" +
                                               "89pIDvs24PsiVzvSzbHo1uxUXvMs92LThZ3Xf4welSfHc28MIRX+bRQauSXw0f6U\n" +
                                               "wmATvQf68KfTaZCQtlhQGLgOQj1rD7I0i4br70aUi5H7Vce/KhXDWlex8UiCqLWf\n" +
                                               "EhPClgfq+qb2aG45QQHfPwCiCB6nw+Hxka9XWkrpzIJ23OhG3/Ojuu2JiE9EpGom\n" +
                                               "+QAXgD0Uplog5qaMrO5nnUoSy8cii0sf7f1Ml83TcoWoSXJck57WZvMCs6UGs051\n" +
                                               "/Dp/wsFejs8sYnR/l3h3Z3nD43ZNyJ3vtC6o3E9K6ZVJTzizqsYbCahWV+8wDLZ/\n" +
                                               "4Vg1+IlO0b0K1qLxsjycEuRGwlDiJaruFl1C+UbnZcq7bdmsSLiHS6+vhEoMdXhV\n" +
                                               "2eGGOBM4F5msGkxbkapbkfv8gj3YkDcK2o26eIRtSudFoLBqszu/C2/snIabuPLV\n" +
                                               "drNPuchfq60ghYJzHeIY0F3dRu2QXWNn53icDE+STnrHYoOw+vZPB5dDVckIgDrj\n" +
                                               "pKj6f0bllSYj4bIA6M3wz7yhEEi6VE9OT6h1BX4/Hr6Oao3qH0id5LwLLSgzl6F7\n" +
                                               "L5cxFOXYI06acTOeSZehAoIBAQDXb/G3ld7xjTzafgKrSUDlwb3QCxxNR1+Y3+K2\n" +
                                               "Fe6B6XvCInnkBI4BU0Z/kK+GU1mpBT5lREqO5icXfi3Wx606tuC/a3itrCHrdDTh\n" +
                                               "aHFjl4wnclrK1BrOBN1ThOhg+8QjxgbwDbHQ81a9rndNQHje42ZSiQAL+pYd6T+5\n" +
                                               "M7oaAyTwiTEDFelSoevuHbRkWmNGeVCGZJJEJEIjgnlBsfjp8A7N9Qb7DHXtj6jP\n" +
                                               "HANevDt5i6VdY89GbYYNUD2bP1ZKSfRRHvvocu7bFwr0pCDANhyLaBJOOy9twDr+\n" +
                                               "FfFFLTsm8yc+DFBbULTmY8ME5uO9B59gK6+6xZL0tN9gJ+YRAoIBAQDR5NtCkZUp\n" +
                                               "pSJywPXdvhcf0cyxvAln8gTzGPHBn+szoqNIVWZYTKAjI4yUilqgd3VmkVbwzSMU\n" +
                                               "gegUScrqhFXsuMU8pgCPkxIcOTPdIuSmokhIigtwhTNWRfSyqwiDtpeO6VrF+miR\n" +
                                               "BToJjQaNFk//QLhq+a4qOBqrX7/PtVc1TMUyNMiDXFn0AkRag0MkLo9fkDCQgolV\n" +
                                               "w1dU5udr2LzSEm1B+dGK6p0VLJgn2kIFT6BmbHHdNuW4t532Vc2zX0AQe288oh6u\n" +
                                               "dGlHoRpKewQOHNnLjhn7l1mGv+oEbGlwaq7eUHz+GiD+rSbhOS/QjdONe/cMqo5T\n" +
                                               "EB6w9L9a/YK7AoIBACRD29SjjdvrgorlG13p/tquOl2DAUig8x6w0WEFYBjOTN7p\n" +
                                               "HsubWKwwcHWYzXM3JKinEVHKpSJY68uwmdbF1gtELaELXk0d5LfV3/Dxu+Sf5h/d\n" +
                                               "yBrMiZaUiw08GkH5H5NGCnTuWThrPfbAH6UJbU9XyCmsli1uCUcPtIJgirtGPDmh\n" +
                                               "Xna+gYA/cY+rwGoELSH32e5Fj7mYwOlpVTAR3WzD1DonPP2Vo2RSAoCanpab6QcU\n" +
                                               "0sldu86HMUGceEJh7wyiVlYxeQYwErUes+Fqn3i7oyJ0amBw5hL7gPK0juCuNH7h\n" +
                                               "/4EaYYx9kXYW5QU2OK/hUJrHv9UY3RwENnXhQYECggEBAIcD7RrkJQxV4lPo1f9e\n" +
                                               "oOdiAIcwCujnYNGzcQf5Q8XCT8Be1ufj2nrgCjUezm88iLOCuGdLvc4aRlyOn029\n" +
                                               "9LvCm3WI3wF8PIEVNsx//o9GArNOwU8PD9fmRiKMLHz1foZ6i16g1pS6xPuR0O3+\n" +
                                               "tVTfoAGIPMWBs34bqHoHD2ME0DCcjYMaa+6vaqLCnvTuUmHJkcPThF47uritk53n\n" +
                                               "HIcRPWDcPzNZ+dO+DN5N4nwiHW7lQVVoU9s/mgf0Z86DbeVsUUCylIPp9DMUaaIR\n" +
                                               "galGW7852HLjh75LQ1C3IBglN/lf0xdtXV4VqdXlAGHqaXQwktl9+PFrhCKWPWVd\n" +
                                               "f7MCggEAHWHq/n+mM0+Tl3o+VF7+iDnzQ91+zN7PdHArrVLEmaipxgmMtAX2A/ud\n" +
                                               "XJf8NmCcULeI6jvBRU2P/uCKNOQy6rUncpK/sKdfBBADb7wqy6tGEXtVe+JiGTqs\n" +
                                               "lCZFINMYnO/n1be5oA+q4hz5EBwujP1TJG2eHgFeHMJKzfYKbZM0pMHFXBMxKV4G\n" +
                                               "JV2M6pM2YZcmEfWM+zY1UiMEbiqCnlpCsxjZthQg4T/uMGaEVeCVB3/aUsusWZRJ\n" +
                                               "7hf4/13gqTDI612Q3qQRVlsMBITkhs+psLsrTi5s9YxMUAn/unwq0ErBM1Vm8isW\n" +
                                               "ndl6Zvqa954h5bVtyL/qs7HvK5b7xQ==\n" +
                                               "-----END PRIVATE KEY-----\n";

    public static final String pemPublicKey = "-----BEGIN PUBLIC KEY-----\n" +
                                              "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAsKL8jvIEy2peLtEvyhWW\n" +
                                              "b/O/9RHTfPXjeXahXmVrXE4zY5CJ6Mf1PFkwQ8K8S35YhSbOZM4aYhF9V8F4jwyW\n" +
                                              "nX6qWUMrWVHOuS32fkjdNo0z/KxCbG5nRIWLuv/PkHNuIJqMCbwn6Qud5a+wxeLg\n" +
                                              "LqlroCtUJKAGj4YlZ5i8oMdCqfHKl/DMwcks5XxtIArz6GcM2z8fOB3NRexj32MU\n" +
                                              "LH7ybWhCDx/RSqGQYJ8sWEFIK4HSmYqwqIQpFAm/ixISkeWBL6ikgqchZNMf7xyn\n" +
                                              "yJxjCHgtkxANsQhHj2kgAzLDeBsuM+/WRhBGa+LRvEcuu/zZv9+7eVhpaYJveLVd\n" +
                                              "cwPewW/8liBmKIzj/QPCn7ZlVRk094TZD6TCER4+JFW9mo0vFD8S9o0zhMlckzCF\n" +
                                              "4ZNNgyP9tI8Wecq25A+sUY5/WZNLi+mka/GnfPt97GrhM0YHb1M6t4nh1R437Nwh\n" +
                                              "rUHR/YDazbBvLk5T71GgfQfn44L9SwsqEYaHvdZAfV0IZJBtDo/yCe/yvgtHTymB\n" +
                                              "eBrRMpBU5recPtW8bgEWlHl6Qyduw9EBJjNYxvBpgV/D/tNBcau0aGxmhwpBevet\n" +
                                              "ekV6XA2miC7rWu2Wrq2l5LjXEgZOD5PNN2vQS2Cdet9JHYWbVbK3mBLgoChcC5Xo\n" +
                                              "/QHLU4RydI0i0+Z2/tjGsGsCAwEAAQ==\n" +
                                              "-----END PUBLIC KEY-----\n";

    private static final String otherPemPublicKey = "-----BEGIN PUBLIC KEY-----\n" +
                                                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1ouH9SLS6J2a5WkzAD86\n" +
                                                    "TJNosNhacGJloUQyxfTsOLJXdKJkK5/kD101bTwdYRqMx9vR2l8zSXdpUjsn45qD\n" +
                                                    "2Nu0fvO4npIfeTfZDhXFsX7/aSW+/AJbGE/Zz3Oi7fGrndlgkCe3J3iqMtPMvQ0T\n" +
                                                    "JbKWmh+EonNR3nkdapxlI8+gEqAODQTZuGT5/juZztMr1hsx83KpWaYnkj/t8b9M\n" +
                                                    "wWhThcmk5zjuoQCT4mF8pFe+r8V9V59g4Y+G/u1xs1+vDH2sBULOem2OPGBnnslE\n" +
                                                    "hOZEOK1l6HX61MF/9d26LkYWoWpzBkaCwl1Lw4GslXFr1F4+8RoGT1xmkZQmb+f/\n" +
                                                    "XwIDAQAB\n" +
                                                    "-----END PUBLIC KEY-----\n";

    private static final byte[] message = ("Hello,\n" +
                                           "\n" +
                                           "this is a secret message.\n" +
                                           "\n" +
                                           "Yours truly,\n" +
                                           "∠( ᐛ 」∠)＿").getBytes(UTF_8);

    @Test
    public void testEncryption() {
        Key privateKey = parsePrivatePemPkcs8RsaKey(pemPrivateKey);
        Key publicKey = parsePublicPemX509RsaKey(pemPublicKey);

        assertArrayEquals(message, decrypted(encrypted(message, privateKey), publicKey));
        assertArrayEquals(message, decrypted(encrypted(message, publicKey), privateKey));
    }

    @Test
    public void testHashing() throws Exception {
        byte[] hash1 = MessageDigest.getInstance("SHA-256").digest(message);
        byte[] hash2 = sha256Digest(() -> new ByteArrayInputStream(message));
        DigestInputStream digester = sha256Digester(new ByteArrayInputStream(message));
        digester.readAllBytes();
        byte[] hash3 = digester.getMessageDigest().digest();

        assertArrayEquals(hash1, hash2);
        assertArrayEquals(hash1, hash3);
    }

    @Test
    public void testSigning() {
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        RequestSigner signer = new RequestSigner(pemPrivateKey, "myKey", clock);

        URI requestUri = URI.create("https://host:123/path//./../more%2fpath/?yes=no");

        HttpRequest.Builder builder = HttpRequest.newBuilder(requestUri);
        HttpRequest request = signer.signed(builder, Method.GET);

        // GET request with correct signature and URI as-is.
        RequestVerifier verifier = new RequestVerifier(pemPublicKey, clock);
        assertTrue(verifier.verify(Method.valueOf(request.method()),
                                   request.uri(),
                                   request.headers().firstValue("X-Timestamp").get(),
                                   request.headers().firstValue("X-Content-Hash").get(),
                                   request.headers().firstValue("X-Authorization").get()));

        // POST request with correct signature and URI normalized.
        MultiPartStreamer streamer = new MultiPartStreamer().addText("message", new String(message, UTF_8))
                                                            .addBytes("copy", message);
        request = signer.signed(builder, Method.POST, streamer);
        assertTrue(verifier.verify(Method.valueOf(request.method()),
                                   request.uri().normalize(),
                                   request.headers().firstValue("X-Timestamp").get(),
                                   request.headers().firstValue("X-Content-Hash").get(),
                                   request.headers().firstValue("X-Authorization").get()));

        // Wrong method.
        assertFalse(verifier.verify(Method.PATCH,
                                    request.uri().normalize(),
                                    request.headers().firstValue("X-Timestamp").get(),
                                    request.headers().firstValue("X-Content-Hash").get(),
                                    request.headers().firstValue("X-Authorization").get()));

        // Wrong path.
        assertFalse(verifier.verify(Method.valueOf(request.method()),
                                    request.uri().resolve("asdf"),
                                    request.headers().firstValue("X-Timestamp").get(),
                                    request.headers().firstValue("X-Content-Hash").get(),
                                    request.headers().firstValue("X-Authorization").get()));

        // Wrong timestamp.
        assertFalse(verifier.verify(Method.valueOf(request.method()),
                                    request.uri().normalize(),
                                    Instant.EPOCH.plusMillis(1).toString(),
                                    request.headers().firstValue("X-Content-Hash").get(),
                                    request.headers().firstValue("X-Authorization").get()));

        // Wrong content hash.
        assertFalse(verifier.verify(Method.valueOf(request.method()),
                                    request.uri().normalize(),
                                    request.headers().firstValue("X-Timestamp").get(),
                                    "Wrong/hash",
                                    request.headers().firstValue("X-Authorization").get()));

        // Wrong signature.
        assertFalse(verifier.verify(Method.valueOf(request.method()),
                                    request.uri().normalize(),
                                    request.headers().firstValue("X-Timestamp").get(),
                                    request.headers().firstValue("X-Content-Hash").get(),
                                    "Wrong/signature"));

        // Key pair mismatch.
        verifier = new RequestVerifier(otherPemPublicKey, clock);
        assertFalse(verifier.verify(Method.valueOf(request.method()),
                                    request.uri().normalize(),
                                    request.headers().firstValue("X-Timestamp").get(),
                                    request.headers().firstValue("X-Content-Hash").get(),
                                    request.headers().firstValue("X-Authorization").get()));

        // Too old request.
        verifier = new RequestVerifier(pemPublicKey, Clock.fixed(Instant.EPOCH.plusSeconds(301), ZoneOffset.UTC));
        assertFalse(verifier.verify(Method.valueOf(request.method()),
                                    request.uri().normalize(),
                                    request.headers().firstValue("X-Timestamp").get(),
                                    request.headers().firstValue("X-Content-Hash").get(),
                                    request.headers().firstValue("X-Authorization").get()));

        // Too new request.
        verifier = new RequestVerifier(pemPublicKey, Clock.fixed(Instant.EPOCH.minusSeconds(301), ZoneOffset.UTC));
        assertFalse(verifier.verify(Method.valueOf(request.method()),
                                    request.uri().normalize(),
                                    request.headers().firstValue("X-Timestamp").get(),
                                    request.headers().firstValue("X-Content-Hash").get(),
                                    request.headers().firstValue("X-Authorization").get()));

    }

}
