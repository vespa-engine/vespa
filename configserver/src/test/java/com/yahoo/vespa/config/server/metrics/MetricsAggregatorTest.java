package com.yahoo.vespa.config.server.metrics;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.http.v2.MetricsResponse;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.*;


/**
 * @author olaa
 */
public class MetricsAggregatorTest {

    @Rule
    public final WireMockRule wireMock = new WireMockRule(options().port(8080), true);

    @Test
    public void testMetricAggregation() throws IOException {
        MetricsAggregator metricsAggregator = new MetricsAggregator();

        ApplicationId applicationId = ApplicationId.from("tenant", "app", "default");
        Map<String, List<URI>> clusterHosts = Map.of(
                "cluster1", List.of(URI.create("http://localhost:8080/1"), URI.create("http://localhost:8080/2")),
                "cluster2", List.of(URI.create("http://localhost:8080/3"))
        );
        Map<ApplicationId, Map<String, List<URI>>> applications = Map.of(applicationId, clusterHosts);

        stubFor(get(urlEqualTo("/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(metricsString(10,20,33,40,50))));

        stubFor(get(urlEqualTo("/2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(metricsString(1,2,3,4,5))));

        stubFor(get(urlEqualTo("/3"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(metricsString(1,2,3,4,5))));

        MetricsResponse metricsResponse = metricsAggregator.aggregateAllMetrics(applications);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        metricsResponse.render(bos);
        String expectedResponse = "[\n" +
                " {\n" +
                "  \"applicationId\": \"tenant:app:default\",\n" +
                "  \"clusters\": [\n" +
                "   {\n" +
                "    \"clusterName\": \"cluster1\",\n" +
                "    \"queriesPerSecond\": 2.8666666666666667,\n" +
                "    \"writesPerSecond\": 1.4333333333333333,\n" +
                "    \"documentCount\": 6000.0,\n" +
                "    \"queryLatencyMillis\": 116.27906976744185,\n" +
                "    \"feedLatency\": 69.76744186046511,\n" +
                "    \"timestamp\": 1557306075\n" +
                "   },\n" +
                "   {\n" +
                "    \"clusterName\": \"cluster2\",\n" +
                "    \"queriesPerSecond\": 1.4333333333333333,\n" +
                "    \"writesPerSecond\": 0.7166666666666667,\n" +
                "    \"documentCount\": 3000.0,\n" +
                "    \"queryLatencyMillis\": 116.27906976744185,\n" +
                "    \"feedLatency\": 69.76744186046511,\n" +
                "    \"timestamp\": 1557306075\n" +
                "   }\n" +
                "  ]\n" +
                " }\n" +
                "]\n";
        assertEquals(expectedResponse, bos.toString());
        wireMock.stop();
    }

    private String metricsString(double queriesPerSecond, double writesPerSecond, double documentCount, double queryLatencyMillis, double writeLatencyMills) throws IOException {
        String responseBody = Files.readString(Path.of("src/test/resources/metrics_response"));
        return String.format(responseBody, queriesPerSecond, writesPerSecond, documentCount, queryLatencyMillis, writeLatencyMills);
    }
}