package ai.vespa.hosted.api;

import java.net.URI;
import java.security.Key;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * Verifies that signed HTTP requests match the indicated public key.
 *
 * @author jonmv
 */
public class RequestVerifier {

    private final Key publicKey;
    private final Clock clock;

    public RequestVerifier(String pemPublicKey) {
        this(pemPublicKey, Clock.systemUTC());
    }

    RequestVerifier(String pemPublicKey, Clock clock) {
        this.publicKey = Signatures.parsePublicPemX509RsaKey(pemPublicKey);
        this.clock = clock;
    }

    /** Returns whether the given data is a valid request now, as dictated by timestamp and the decryption key of this. */
    public boolean verify(Method method, URI requestUri, String timestamp, String contentHash, String signature) {
        try {
            Instant now = clock.instant(), then = Instant.parse(timestamp);
            if (Duration.between(now, then).abs().compareTo(Duration.ofMinutes(5)) > 0)
                return false; // Timestamp mismatch between sender and receiver of more than 5 minutes is not acceptable.

            byte[] canonicalMessage = Signatures.canonicalMessageOf(method.name(), requestUri, timestamp, contentHash);
            byte[] decrypted = Signatures.decrypted(Base64.getDecoder().decode(signature), publicKey);
            return Arrays.equals(canonicalMessage, decrypted);
        }
        catch (RuntimeException e) {
            return false;
        }
    }

}
