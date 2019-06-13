package ai.vespa.hosted.api;

import javax.net.ssl.SSLContext;
import java.net.http.HttpRequest;
import java.util.Optional;

/**
 * Adds environment dependent authentication to HTTP request against hosted Vespa API and deployments.
 *
 * @author jonmv
 */
public interface Authenticator {

    /** Returns an SSLContext which provides authentication against a Vespa endpoint. */
    SSLContext sslContext();

    /** Adds necessary authentication to the given HTTP request builder, to pass the data plane of a Vespa endpoint. */
    HttpRequest.Builder authenticated(HttpRequest.Builder request);

    /** Returns a client authenticated to talk to the hosted Vespa API. */
    ControllerHttpClient controller();

}
