// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * An endpoint in a Vespa application {@link Deployment}, which allows document retrieval.
 *
 * @author jonmv
 */
public interface Endpoint {

    /** Returns the URI of the endpoint, with scheme, host and port. */
    URI uri();

    /** Returns the authenticator for HTTP requests against this particular endpoint. */
    EndpointAuthenticator authenticator();

    /** Sends the given request with required authentication. */
    <T> HttpResponse<T> send(HttpRequest.Builder request, HttpResponse.BodyHandler<T> handler);

    /** Sends the given request with required authentication. */
    default HttpResponse<String> send(HttpRequest.Builder request) {
        return send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** Creates a request against the endpoint, with the given path and properties. */
    HttpRequest.Builder request(String path, Map<String, String> properties);

    /** Creates a request against the endpoint, with the given path. */
    default HttpRequest.Builder request(String path) {
        return request(path, Collections.emptyMap());
    }

}
