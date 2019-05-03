// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureUtils;

import java.net.URI;
import java.security.Signature;
import java.security.SignatureException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;

/**
 * Verifies that signed HTTP requests match the indicated public key.
 *
 * @author jonmv
 */
public class RequestVerifier {

    private final Signature verifier;
    private final Clock clock;

    /** Creates a new request verifier from the given PEM encoded ECDSA public key. */
    public RequestVerifier(String pemPublicKey) {
        this(pemPublicKey, Clock.systemUTC());
    }

    public RequestVerifier(String pemPublicKey, Clock clock) {
        this.verifier = SignatureUtils.createVerifier(KeyUtils.fromPemEncodedPublicKey(pemPublicKey), SHA256_WITH_ECDSA);
        this.clock = clock;
    }

    /** Returns whether the given data is a valid request now, as dictated by timestamp and the decryption key of this. */
    public boolean verify(Method method, URI requestUri, String timestamp, String contentHash, String signature) {
        try {
            Instant now = clock.instant(), then = Instant.parse(timestamp);
            if (Duration.between(now, then).abs().compareTo(Duration.ofMinutes(5)) > 0)
                return false; // Timestamp mismatch between sender and receiver of more than 5 minutes is not acceptable.

            byte[] canonicalMessage = Signatures.canonicalMessageOf(method.name(), requestUri, timestamp, contentHash);
            verifier.update(canonicalMessage);
            return verifier.verify(Base64.getDecoder().decode(signature));
        }
        catch (RuntimeException | SignatureException e) {
            return false;
        }
    }

}
