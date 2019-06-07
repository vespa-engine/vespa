package ai.vespa.hosted.cd.http;

import ai.vespa.hosted.auth.Authenticator;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.Slime;
import ai.vespa.hosted.cd.Digest;
import ai.vespa.hosted.cd.Feed;
import ai.vespa.hosted.cd.Query;
import ai.vespa.hosted.cd.Search;
import ai.vespa.hosted.cd.Selection;
import ai.vespa.hosted.cd.TestEndpoint;
import ai.vespa.hosted.cd.Visit;
import ai.vespa.hosted.cd.metric.Metrics;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static java.util.Objects.requireNonNull;

public class HttpEndpoint implements TestEndpoint {

    static final String metricsPath = "/state/v1/metrics";
    static final String documentApiPath = "/document/v1";
    static final String searchApiPath = "/search";

    private final URI endpoint;
    private final HttpClient client;
    private final Authenticator authenticator;

    public HttpEndpoint(URI endpoint) {
        this.endpoint = requireNonNull(endpoint);
        this.authenticator = new Authenticator();
        this.client = HttpClient.newBuilder()
                                .sslContext(authenticator.sslContext())
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
            if (response.statusCode() / 100 != 2) // TODO consider allowing 504 if specified.
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
