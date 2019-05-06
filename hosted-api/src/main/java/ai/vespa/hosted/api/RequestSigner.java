// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.security.Signature;
import java.security.SignatureException;
import java.time.Clock;
import java.util.Base64;
import java.util.function.Supplier;

import static ai.vespa.hosted.api.Signatures.sha256Digest;
import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;

/**
 * Signs HTTP request headers using a private key, for verification by the indicated public key.
 *
 * @author  jonmv
 */
public class RequestSigner {

    private final Signature signer;
    private final String keyId;
    private final Clock clock;

    /** Creates a new request signer from the given PEM encoded ECDSA key, with a public key with the given ID. */
    public RequestSigner(String pemPrivateKey, String keyId) {
        this(pemPrivateKey, keyId, Clock.systemUTC());
    }

    /** Creates a new request signer with a custom clock. */
    public RequestSigner(String pemPrivateKey, String keyId, Clock clock) {
        this.signer = SignatureUtils.createSigner(KeyUtils.fromPemEncodedPrivateKey(pemPrivateKey), SHA256_WITH_ECDSA);
        this.keyId = keyId;
        this.clock = clock;
    }

    /**
     * Completes, signs and returns the given request builder and data.<br>
     * <br>
     * The request builder's method and data are set to the given arguments, and a hash of the
     * content is computed and added to a header, together with other meta data, like the URI
     * of the request, the current UTC time, and the id of the public key which shall be used to
     * verify this signature.
     * Finally, a signature is computed from these fields, based on the private key of this, and
     * added to the request as another header.
     */
    public HttpRequest signed(HttpRequest.Builder request, Method method, Supplier<InputStream> data) {
        try {
            String timestamp = clock.instant().toString();
            String contentHash = Base64.getEncoder().encodeToString(sha256Digest(data::get));
            byte[] canonicalMessage = Signatures.canonicalMessageOf(method.name(), request.copy().build().uri(), timestamp, contentHash);
            signer.update(canonicalMessage);
            String signature = Base64.getEncoder().encodeToString(signer.sign());

            request.setHeader("X-Timestamp", timestamp);
            request.setHeader("X-Content-Hash", contentHash);
            request.setHeader("X-Key-Id", keyId);
            request.setHeader("X-Authorization", signature);

            request.method(method.name(), HttpRequest.BodyPublishers.ofInputStream(data));
            return request.build();
        }
        catch (SignatureException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
