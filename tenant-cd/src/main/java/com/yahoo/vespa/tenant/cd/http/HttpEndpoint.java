package com.yahoo.vespa.tenant.cd.http;

import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.tenant.cd.Digest;
import com.yahoo.vespa.tenant.cd.Feed;
import com.yahoo.vespa.tenant.cd.Query;
import com.yahoo.vespa.tenant.cd.Search;
import com.yahoo.vespa.tenant.cd.Selection;
import com.yahoo.vespa.tenant.cd.TestEndpoint;
import com.yahoo.vespa.tenant.cd.Visit;
import com.yahoo.vespa.tenant.cd.metrics.Metrics;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class HttpEndpoint implements TestEndpoint {

    static final String metricsPath = "/state/v1/metrics";
    static final String documentApiPath = "/document/v1";
    static final String searchApiPath = "/search";

    private final URI endpoint;
    private final HttpClient client;

    public HttpEndpoint(URI endpoint) {
        this.endpoint = requireNonNull(endpoint);
        this.client = HttpClient.newBuilder()
                                .sslContext(Security.sslContext())
                                .connectTimeout(Duration.ofSeconds(5))
                                .version(HttpClient.Version.HTTP_1_1)
                                .build();
    }

    @Override
    public Digest digest(Feed feed) {
        return null;
    }

    @Override
    public Search search(Query query) {
        try {
            URI target = endpoint.resolve(searchApiPath).resolve("?" + query.rawQuery());
            HttpRequest request = HttpRequest.newBuilder()
                                             .timeout(Duration.ofSeconds(5))
                                             .uri(target)
                                             .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2)
                throw new RuntimeException("Non-OK status code " + response.statusCode() + " at " + target +
                                           ", with response \n" + new String(response.body()));

            return toSearch(response.body());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static Search toSearch(byte[] body) {
        Inspector rootObject = new JsonDecoder().decode(new Slime(), body).get();
        // TODO jvenstad
        return new Search();
    }

    @Override
    public Visit visit(Selection selection) {
        return null;
    }

    @Override
    public Metrics metrics() {
        return null;
    }

}
