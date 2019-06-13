package ai.vespa.hosted.api;

import javax.net.ssl.SSLContext;
import java.net.http.HttpRequest;
import java.util.Optional;

/**
 * Adds environment dependent authentication to HTTP request against Vespa deployments.
 *
 * @author jonmv
 */
public interface EndpointAuthenticator {

    /** Returns an SSLContext which provides authentication against a Vespa endpoint. */
    SSLContext sslContext();

    /** Adds necessary authentication to the given HTTP request builder, to pass the data plane of a Vespa endpoint. */
    HttpRequest.Builder authenticated(HttpRequest.Builder request);

}
