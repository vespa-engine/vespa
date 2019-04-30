package ai.vespa.hosted.api;

import com.yahoo.security.KeyUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.security.Signature;
import java.security.SignatureException;
import java.time.Clock;
import java.util.Base64;
import java.util.function.Supplier;

import static ai.vespa.hosted.api.Signatures.sha256Digest;

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
        this.signer = KeyUtils.createSigner(KeyUtils.fromPemEncodedPrivateKey(pemPrivateKey));
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

    /**
     * Completes, signs and returns the given request builder and data.
     *
     * This sets the Content-Type header from the given streamer, and returns
     * {@code signed(request, method, streamer::data)}.
     */
    public HttpRequest signed(HttpRequest.Builder request, Method method, MultiPartStreamer streamer) {
        request.setHeader("Content-Type", streamer.contentType());
        return signed(request, method, streamer::data);
    }

    /**
     * Completes, signs and returns the given request builder.<br>
     * <br>
     * This is simply a convenience for<br>
     * {@code signed(request, method, () -> new ByteArrayInputStream(data))}.
     */
    public HttpRequest signed(HttpRequest.Builder request, Method method, byte[] data) {
        return signed(request, method, () -> new ByteArrayInputStream(data));
    }

    /**
     * Completes, signs and returns the given request builder.<br>
     * <br>
     * This sets the data of the request to be empty, and returns <br>
     * {@code signed(request, method, InputStream::nullInputStream)}.
     */
    public HttpRequest signed(HttpRequest.Builder request, Method method) {
        return signed(request, method, InputStream::nullInputStream);
    }

}
