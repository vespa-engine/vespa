// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd;

import javax.net.ssl.SSLContext;
import java.net.http.HttpRequest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/**
 * Adds environment dependent authentication to HTTP request against Vespa deployments.
 *
 * An implementation typically needs to override either of the methods in this interface,
 * and needs to run in different environments, e.g., local user testing and automatic testing
 * in a deployment pipeline.
 *
 * @author jonmv
 */
public interface EndpointAuthenticator {

    /** Returns an SSLContext which provides authentication against a Vespa endpoint. */
    default SSLContext sslContext() {
        try {
            return SSLContext.getDefault();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /** Adds necessary authentication data to the given HTTP request builder, to pass the data plane of a Vespa endpoint. */
    default HttpRequest.Builder authenticated(HttpRequest.Builder request) {
        Map<String, String> authorizationHeaders = authorizationHeaders();
        if (authorizationHeaders.isEmpty())
            return request;

        Map<String, List<String>> headers = request.build().headers().map();
        authorizationHeaders.forEach((name, value) -> {
            if ( ! headers.containsKey(name))
                request.setHeader(name, value);
        });
        return request;
    }

    default Map<String, String> authorizationHeaders() {
        return Map.of();
    }

}
