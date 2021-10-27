// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static ai.vespa.hosted.api.Signatures.sha256Digest;
import static ai.vespa.hosted.api.Signatures.sha256Digester;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that messages can be signed and verified, and that the keys used for this can be parsed.
 *
 * To generate appropriate keys, run the following commands:
 * <ol>
 * <li>{@code openssl ecparam -name prime256v1 -genkey -noout -out private_key.pem}</li>
 * <li>{@code openssl ec -pubout -in private_key.pem -out public_key.pem}</li>
 * </ol>
 * Step 1 generates a private key and Step 2 extracts and writes the public key to a separate file.
 *
 * @author jonmv
 */
class SignaturesTest {

    private static final String ecPemPublicKey = "-----BEGIN PUBLIC KEY-----\n" +
                                                 "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuKVFA8dXk43kVfYKzkUqhEY2rDT9\n" +
                                                 "z/4jKSTHwbYR8wdsOSrJGVEUPbS2nguIJ64OJH7gFnxM6sxUVj+Nm2HlXw==\n" +
                                                 "-----END PUBLIC KEY-----\n";

    private static final String ecPemPrivateKey = "-----BEGIN EC PRIVATE KEY-----\n" +
                                                  "MHcCAQEEIJUmbIX8YFLHtpRgkwqDDE3igU9RG6JD9cYHWAZii9j7oAoGCCqGSM49\n" +
                                                  "AwEHoUQDQgAEuKVFA8dXk43kVfYKzkUqhEY2rDT9z/4jKSTHwbYR8wdsOSrJGVEU\n" +
                                                  "PbS2nguIJ64OJH7gFnxM6sxUVj+Nm2HlXw==\n" +
                                                  "-----END EC PRIVATE KEY-----\n";

    private static final String otherEcPemPublicKey = "-----BEGIN PUBLIC KEY-----\n" +
                                                      "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEFELzPyinTfQ/sZnTmRp5E4Ve/sbE\n" +
                                                      "pDhJeqczkyFcT2PysJ5sZwm7rKPEeXDOhzTPCyRvbUqc2SGdWbKUGGa/Yw==\n" +
                                                      "-----END PUBLIC KEY-----\n";

    private static final byte[] message = ("Hello,\n" +
                                           "\n" +
                                           "this is a secret message.\n" +
                                           "\n" +
                                           "Yours truly,\n" +
                                           "∠( ᐛ 」∠)＿").getBytes(UTF_8);

    @Test
    void testHashing() throws Exception {
        byte[] hash1 = MessageDigest.getInstance("SHA-256").digest(message);
        byte[] hash2 = sha256Digest(() -> new ByteArrayInputStream(message));
        DigestInputStream digester = sha256Digester(new ByteArrayInputStream(message));
        digester.readAllBytes();
        byte[] hash3 = digester.getMessageDigest().digest();

        assertArrayEquals(hash1, hash2);
        assertArrayEquals(hash1, hash3);
    }

    @Test
    void testSigning() {
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        RequestSigner signer = new RequestSigner(ecPemPrivateKey, "myKey", clock);

        URI requestUri = URI.create("https://host:123/path//./../more%2fpath/?yes=no");
        HttpRequest.Builder builder = HttpRequest.newBuilder(requestUri);
        HttpRequest request = signer.signed(builder, Method.GET, InputStream::nullInputStream);

        // GET request with correct signature and URI as-is.
        RequestVerifier verifier = new RequestVerifier(ecPemPublicKey, clock);
        assertTrue(verifier.verify(Method.valueOf(request.method()),
                                   request.uri(),
                                   request.headers().firstValue("X-Timestamp").get(),
                                   request.headers().firstValue("X-Content-Hash").get(),
                                   request.headers().firstValue("X-Authorization").get()));

        // POST request with correct signature and URI normalized.
        MultiPartStreamer streamer = new MultiPartStreamer().addText("message", new String(message, UTF_8))
                                                            .addBytes("copy", message);
        request = signer.signed(builder.setHeader("Content-Type", streamer.contentType()), Method.POST, streamer::data);
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
        verifier = new RequestVerifier(otherEcPemPublicKey, clock);
        assertFalse(verifier.verify(Method.valueOf(request.method()),
                                    request.uri().normalize(),
                                    request.headers().firstValue("X-Timestamp").get(),
                                    request.headers().firstValue("X-Content-Hash").get(),
                                    request.headers().firstValue("X-Authorization").get()));

        // Too old request.
        verifier = new RequestVerifier(ecPemPublicKey, Clock.fixed(Instant.EPOCH.plusSeconds(301), ZoneOffset.UTC));
        assertFalse(verifier.verify(Method.valueOf(request.method()),
                                    request.uri().normalize(),
                                    request.headers().firstValue("X-Timestamp").get(),
                                    request.headers().firstValue("X-Content-Hash").get(),
                                    request.headers().firstValue("X-Authorization").get()));

        // Too new request.
        verifier = new RequestVerifier(ecPemPublicKey, Clock.fixed(Instant.EPOCH.minusSeconds(301), ZoneOffset.UTC));
        assertFalse(verifier.verify(Method.valueOf(request.method()),
                                    request.uri().normalize(),
                                    request.headers().firstValue("X-Timestamp").get(),
                                    request.headers().firstValue("X-Content-Hash").get(),
                                    request.headers().firstValue("X-Authorization").get()));

    }

}
