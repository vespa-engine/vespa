/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.http.application.NodeMetricsClient.Node;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.yahoo.test.ManualClock;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

import static ai.vespa.metricsproxy.TestUtil.getFileContents;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertEquals;

/**
 * @author gjoranv
 */
public class NodeMetricsClientTest {

    private static final String TEST_FILE = "generic-sample.json";
    private static final String RESPONSE = getFileContents(TEST_FILE);
    private static final int PORT = getAvailablePort();

    private static final CloseableHttpClient httpClient = HttpClients.createDefault();

    private final Node node = new Node("id", "localhost", PORT);
    private ManualClock clock;
    private NodeMetricsClient nodeMetricsClient;

    @Before
    public void setup() {
        clock = new ManualClock();
        nodeMetricsClient = new NodeMetricsClient(httpClient, node, clock);
    }

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().port(PORT));

    @Test
    public void metrics_are_not_retrieved_until_first_request() {
        assertEquals(0, nodeMetricsClient.snapshotsRetrieved());
    }

    @Test
    public void metrics_are_retrieved_upon_first_request() {
        stubFor(get(urlEqualTo(node.metricsUri.getPath()))
                        .willReturn(aResponse().withBody(RESPONSE)));

        List<MetricsPacket.Builder> metrics = nodeMetricsClient.getMetrics();
        assertEquals(1, nodeMetricsClient.snapshotsRetrieved());
        assertEquals(4, metrics.size());
   }

    @Test
    public void cached_metrics_are_used_when_ttl_has_not_expired() {
        stubFor(get(urlEqualTo(node.metricsUri.getPath()))
                        .willReturn(aResponse().withBody(RESPONSE)));

        nodeMetricsClient.getMetrics();
        assertEquals(1, nodeMetricsClient.snapshotsRetrieved());

        clock.advance(NodeMetricsClient.METRICS_TTL.minusMillis(1));
        nodeMetricsClient.getMetrics();
        assertEquals(1, nodeMetricsClient.snapshotsRetrieved());
    }

    @Test
    public void metrics_are_refreshed_when_ttl_has_expired() {
        stubFor(get(urlEqualTo(node.metricsUri.getPath()))
                        .willReturn(aResponse().withBody(RESPONSE)));

        nodeMetricsClient.getMetrics();
        assertEquals(1, nodeMetricsClient.snapshotsRetrieved());

        clock.advance(NodeMetricsClient.METRICS_TTL.plusMillis(1));
        nodeMetricsClient.getMetrics();
        assertEquals(2, nodeMetricsClient.snapshotsRetrieved());
    }

    private static int getAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Could not find available port: ", e);
        }
    }

}
