// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only access to this application's own metrics in Grafana Cloud Mimir, for use by {@link ProductionTest}s.
 *
 * A short-lived, {@code metrics:read} and {@code logs:read}-scoped token is made available to production test jobs only; see
 * {@link #readOnlyToken()}. Queries are restricted, by the token's access policy, to this tenant's own metrics.
 *
 * @author bragehk
 */
public class GrafanaMetrics {

    private static final String TOKEN_SYSTEM_PROPERTY = "vespa.test.grafana.metrics.token";
    private static final URI PROMETHEUS_ENDPOINT = URI.create("https://prometheus-prod-13-prod-us-east-0.grafana.net/api/prom/");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();

    /** Returns the read-only Grafana Cloud metrics token for this run, if one was made available (production test jobs only). */
    public static Optional<String> readOnlyToken() {
        return Optional.ofNullable(System.getProperty(TOKEN_SYSTEM_PROPERTY)).filter(token -> ! token.isBlank());
    }

    /**
     * Runs the given PromQL instant query against Grafana Cloud Mimir, using the token from {@link #readOnlyToken()}.
     *
     * @throws IllegalStateException if no read-only metrics token is available in this run
     */
    public HttpResponse<String> query(String promQlQuery) {
        Objects.requireNonNull(promQlQuery, "promQlQuery must not be null");
        String token = readOnlyToken().orElseThrow(() -> new IllegalStateException(
                "No Grafana Cloud metrics token available -- this is only provided to production test jobs"));
        URI uri = PROMETHEUS_ENDPOINT.resolve("api/v1/query?query=" +
                                              URLEncoder.encode(promQlQuery, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri)
                                         .header("Authorization", "Bearer " + token)
                                         .timeout(REQUEST_TIMEOUT)
                                         .GET()
                                         .build();
        try {
            return httpClient.send(request, BodyHandlers.ofString());
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

}
