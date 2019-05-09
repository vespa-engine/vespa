package com.yahoo.vespa.config.server.metrics;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.http.v2.MetricsRespone;
import com.yahoo.vespa.config.server.tenant.Tenant;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;import com.yahoo.config.model.api.Model;


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
                "cluster1", List.of(URI.create("http://localhost:8080/1"), URI.create("http://localhost:8080/3")),
                "cluster2", List.of(URI.create("http://localhost:8080/3"), URI.create("http://localhost:8080/3"))
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

        MetricsRespone metricsRespone = metricsAggregator.aggregateAllMetrics(applications);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        metricsRespone.render(bos);
        assertEquals(Files.readString(Path.of("src/test/resources/metrics_response")), bos.toString());
        wireMock.stop();
    }

    private String metricsString(double queriesPerSecond, double writesPerSecond, double documentCount, double queryLatencyMillis, double writeLatencyMills) {
        return "{\"metrics\": " +
                "{" +
                "   \"queriesPerSecond\": " + queriesPerSecond + "," +
                "   \"writesPerSecond\": " + writesPerSecond + "," +
                "   \"documentCount\": " + documentCount + "," +
                "   \"queryLatencyMillis\": " + queryLatencyMillis + "," +
                "   \"writeLatencyMills\": " + writeLatencyMills +
                "   }}";
    }
}