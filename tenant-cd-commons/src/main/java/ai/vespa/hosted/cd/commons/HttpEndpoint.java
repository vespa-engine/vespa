// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd.commons;

import ai.vespa.hosted.cd.Endpoint;

import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * A remote endpoint in a {@link HttpDeployment} of a Vespa application, reachable over HTTP.
 *
 * @author jonmv
 */
public class HttpEndpoint implements Endpoint {

    private final URI endpoint;
    private final HttpClient client;
    private final EndpointAuthenticator authenticator;

    public HttpEndpoint(URI endpoint, EndpointAuthenticator authenticator) {
        this.endpoint = requireNonNull(endpoint);
        this.authenticator = requireNonNull(authenticator);
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[] { "TLSv1.2" });
        this.client = HttpClient.newBuilder()
                                .sslContext(authenticator.sslContext())
                                .connectTimeout(Duration.ofSeconds(5))
                                .version(HttpClient.Version.HTTP_1_1)
                                .sslParameters(sslParameters)
                                .build();
    }

    @Override
    public URI uri() {
        return endpoint;
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest.Builder request, HttpResponse.BodyHandler<T> handler) {
        try {
            return client.send(authenticator.authenticated(request).build(), handler);
        }
        catch (IOException | InterruptedException e) {
            throw new RuntimeException(request.build() + " failed: " + e.getMessage(), e);
        }
    }

    @Override
    public HttpRequest.Builder request(String path, Map<String, String> properties) {
        return HttpRequest.newBuilder(endpoint.resolve(path +
                                                       properties.entrySet().stream()
                                                                 .map(entry -> encode(entry.getKey(), UTF_8) + "=" + encode(entry.getValue(), UTF_8))
                                                                 .collect(Collectors.joining("&", path.contains("?") ? "&" : "?", ""))));
    }

}
