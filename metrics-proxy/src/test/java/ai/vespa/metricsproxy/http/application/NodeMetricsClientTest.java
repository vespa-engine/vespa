// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.http.metrics.MetricsV1Handler;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.yahoo.test.ManualClock;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static ai.vespa.metricsproxy.TestUtil.getFileContents;
import static ai.vespa.metricsproxy.http.ValuesFetcher.defaultMetricsConsumerId;
import static ai.vespa.metricsproxy.metric.model.ConsumerId.toConsumerId;
import static ai.vespa.metricsproxy.metric.model.MetricId.toMetricId;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Two optimizations worth noting:
 * <p>
 * 1. Using a ClassRule for the wire mocking means it is reused between test methods.
 * 2. Configuring stubs on the rule is faster than using the static WireMock.stubFor method.
 *
 * @author gjoranv
 */
public class NodeMetricsClientTest {

    private static final String TEST_FILE = "generic-sample.json";
    private static final String RESPONSE = getFileContents(TEST_FILE);
    private static final CloseableHttpAsyncClient httpClient = ApplicationMetricsRetriever.createHttpClient();

    private static final String CPU_METRIC = "cpu.util";
    private static final String REPLACED_CPU_METRIC = "replaced_cpu_util";
    private static final String CUSTOM_CONSUMER = "custom-consumer";
    private static final Duration TTL = Duration.ofSeconds(30);


    private static Node node;

    private NodeMetricsClient nodeMetricsClient;

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(options().dynamicPort());

    @BeforeClass
    public static void setupWireMock() {
        node = new Node("id", "localhost", wireMockRule.port(), MetricsV1Handler.VALUES_PATH);
        URI metricsUri = node.metricsUri(defaultMetricsConsumerId);
        wireMockRule.stubFor(get(urlPathEqualTo(metricsUri.getPath()))
                                     .willReturn(aResponse().withBody(RESPONSE)));

        wireMockRule.stubFor(get(urlPathEqualTo(metricsUri.getPath()))
                                     .withQueryParam("consumer", equalTo(defaultMetricsConsumerId.id))
                                     .willReturn(aResponse().withBody(RESPONSE)));

        // Add a slightly different response for a custom consumer.
        String myConsumerResponse = RESPONSE.replaceAll(CPU_METRIC, REPLACED_CPU_METRIC);
        wireMockRule.stubFor(get(urlPathEqualTo(metricsUri.getPath()))
                                     .withQueryParam("consumer", equalTo(CUSTOM_CONSUMER))
                                     .willReturn(aResponse().withBody(myConsumerResponse)));

    }

    @Before
    public void setupClient() {
        httpClient.start();
        nodeMetricsClient = new NodeMetricsClient(httpClient, node, new ManualClock());
    }

    @Test
    public void metrics_are_not_retrieved_until_first_request() {
        assertEquals(0, nodeMetricsClient.snapshotsRetrieved());
    }

    @Test
    public void metrics_are_retrieved_upon_first_update() throws InterruptedException, ExecutionException {
        assertEquals(0, nodeMetricsClient.getMetrics(defaultMetricsConsumerId).size());
        assertEquals(0, nodeMetricsClient.snapshotsRetrieved());
        updateSnapshot(defaultMetricsConsumerId, TTL);
        assertEquals(1, nodeMetricsClient.snapshotsRetrieved());
        List<MetricsPacket> metrics = nodeMetricsClient.getMetrics(defaultMetricsConsumerId);
        assertEquals(1, nodeMetricsClient.snapshotsRetrieved());
        assertEquals(4, metrics.size());
   }

    @Test
    public void metrics_are_refreshed_on_every_update() {
        assertEquals(0, nodeMetricsClient.snapshotsRetrieved());
        updateSnapshot(defaultMetricsConsumerId, TTL);
        assertEquals(1, nodeMetricsClient.snapshotsRetrieved());
        updateSnapshot(defaultMetricsConsumerId, Duration.ZERO);
        assertEquals(2, nodeMetricsClient.snapshotsRetrieved());
    }

    @Test
    public void metrics_are_not_refreshed_if_ttl_not_expired() {
        assertEquals(0, nodeMetricsClient.snapshotsRetrieved());
        updateSnapshot(defaultMetricsConsumerId, TTL);
        assertEquals(1, nodeMetricsClient.snapshotsRetrieved());
        updateSnapshot(defaultMetricsConsumerId, TTL);
        assertEquals(1, nodeMetricsClient.snapshotsRetrieved());
        updateSnapshot(defaultMetricsConsumerId, Duration.ZERO);
        assertEquals(2, nodeMetricsClient.snapshotsRetrieved());
    }

    @Test
    public void metrics_for_different_consumers_are_cached_separately() {
        updateSnapshot(defaultMetricsConsumerId, TTL);
        List<MetricsPacket> defaultMetrics = nodeMetricsClient.getMetrics(defaultMetricsConsumerId);
        assertEquals(1, nodeMetricsClient.snapshotsRetrieved());
        assertEquals(4, defaultMetrics.size());

        updateSnapshot(toConsumerId(CUSTOM_CONSUMER), TTL);
        List<MetricsPacket> customMetrics = nodeMetricsClient.getMetrics(toConsumerId(CUSTOM_CONSUMER));
        assertEquals(2, nodeMetricsClient.snapshotsRetrieved());
        assertEquals(4, customMetrics.size());

        MetricsPacket replacedCpuMetric = customMetrics.get(0);
        assertTrue(replacedCpuMetric.metrics().containsKey(toMetricId(REPLACED_CPU_METRIC)));
    }

    private void updateSnapshot(ConsumerId consumerId, Duration ttl) {
        var optional = nodeMetricsClient.startSnapshotUpdate(consumerId, ttl);
        optional.ifPresent(future -> {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new AssertionError(e);
            }
        });
    }

}
