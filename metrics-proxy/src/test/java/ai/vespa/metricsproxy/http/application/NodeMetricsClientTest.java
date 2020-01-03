/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.http.MetricsHandler;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.yahoo.test.ManualClock;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.net.URI;
import java.util.List;

import static ai.vespa.metricsproxy.TestUtil.getFileContents;
import static ai.vespa.metricsproxy.http.ValuesFetcher.DEFAULT_PUBLIC_CONSUMER_ID;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertEquals;

/**
 * Two optimizations worth noting:
 *
 * 1. Using a ClassRule for the wire mocking means it is reused between test methods.
 * 2. Configuring stubs on the rule is faster than using the static WireMock.stubFor method.
 *
 * @author gjoranv
 */
public class NodeMetricsClientTest {

    private static final String TEST_FILE = "generic-sample.json";
    private static final String RESPONSE = getFileContents(TEST_FILE);
    private static final CloseableHttpClient httpClient = HttpClients.createDefault();

    private static Node node;

    private ManualClock clock;
    private NodeMetricsClient nodeMetricsClient;

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(options().dynamicPort());

    @BeforeClass
    public static void setupWireMock() {
        node = new Node("id", "localhost", wireMockRule.port(), MetricsHandler.VALUES_PATH);
        URI metricsUri = node.metricsUri(DEFAULT_PUBLIC_CONSUMER_ID);
        wireMockRule.stubFor(get(urlPathEqualTo(metricsUri.getPath()))
                                     .willReturn(aResponse().withBody(RESPONSE)));
    }

    @Before
    public void setupClient() {
        clock = new ManualClock();
        nodeMetricsClient = new NodeMetricsClient(httpClient, node, clock);
    }

    @Test
    public void metrics_are_not_retrieved_until_first_request() {
        assertEquals(0, nodeMetricsClient.snapshotsRetrieved());
    }

    @Test
    public void metrics_are_retrieved_upon_first_request() {
        List<MetricsPacket.Builder> metrics = nodeMetricsClient.getMetrics();
        assertEquals(1, nodeMetricsClient.snapshotsRetrieved());
        assertEquals(4, metrics.size());
   }

    @Test
    public void cached_metrics_are_used_when_ttl_has_not_expired() {
        nodeMetricsClient.getMetrics();
        assertEquals(1, nodeMetricsClient.snapshotsRetrieved());

        clock.advance(NodeMetricsClient.METRICS_TTL.minusMillis(1));
        nodeMetricsClient.getMetrics();
        assertEquals(1, nodeMetricsClient.snapshotsRetrieved());
    }

    @Test
    public void metrics_are_refreshed_when_ttl_has_expired() {
        nodeMetricsClient.getMetrics();
        assertEquals(1, nodeMetricsClient.snapshotsRetrieved());

        clock.advance(NodeMetricsClient.METRICS_TTL.plusMillis(1));
        nodeMetricsClient.getMetrics();
        assertEquals(2, nodeMetricsClient.snapshotsRetrieved());
    }

}
