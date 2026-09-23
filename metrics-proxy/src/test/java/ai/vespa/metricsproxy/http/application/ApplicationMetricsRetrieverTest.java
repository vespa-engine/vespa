// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.metric.model.ConsumerId;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static ai.vespa.metricsproxy.TestUtil.getFileContents;
import static ai.vespa.metricsproxy.http.application.ApplicationMetricsRetriever.MAX_TIMEOUT;
import static ai.vespa.metricsproxy.http.application.ApplicationMetricsRetriever.MIN_TIMEOUT;
import static ai.vespa.metricsproxy.http.application.ApplicationMetricsRetriever.timeout;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class ApplicationMetricsRetrieverTest {

    private static final String TEST_FILE = "generic-sample.json";
    private static final String RESPONSE = getFileContents(TEST_FILE);
    private static final String HOST = "localhost";

    private static int port;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().dynamicPort(), false);

    @Before
    public void setup() {
        port = wireMockRule.port();
    }

    @Test
    public void metrics_can_be_retrieved() {
        var config = nodesConfig("/node0");
        Node node = new Node(config.node(0));

        verifyRetrievingMetricsFromSingleNode(config, node);
    }

    private void verifyRetrievingMetricsFromSingleNode(MetricsNodesConfig config, Node node) {
        wireMockRule.stubFor(get(urlPathEqualTo(config.node(0).metricsPath()))
                                     .willReturn(aResponse().withBody(RESPONSE)));

        ApplicationMetricsRetriever retriever = new ApplicationMetricsRetriever(config);
        retriever.getMetrics();
        retriever.startPollAndWait();
        var metricsByNode = retriever.getMetrics();
        assertEquals(1, metricsByNode.size());
        assertEquals(4, metricsByNode.get(node).size());
    }

    @Test
    public void metrics_can_be_retrieved_from_multiple_nodes() {
        var config = nodesConfig("/node0", "/node1");
        Node node0 = new Node(config.node(0));
        Node node1 = new Node(config.node(1));

        wireMockRule.stubFor(get(urlPathEqualTo(config.node(0).metricsPath()))
                                     .willReturn(aResponse().withBody(RESPONSE)));
        wireMockRule.stubFor(get(urlPathEqualTo(config.node(1).metricsPath()))
                                     .willReturn(aResponse().withBody(RESPONSE)));

        ApplicationMetricsRetriever retriever = new ApplicationMetricsRetriever(config);
        retriever.getMetrics();
        retriever.startPollAndWait();
        var metricsByNode = retriever.getMetrics();
        assertEquals(2, metricsByNode.size());
        assertEquals(4, metricsByNode.get(node0).size());
        assertEquals(4, metricsByNode.get(node1).size());
    }

    @Test
    public void dead_node_yields_empty_metrics() {
        var config = nodesConfig("/non-existent");
        Node node = new Node(config.node(0));

        ApplicationMetricsRetriever retriever = new ApplicationMetricsRetriever(config);
        var metricsByNode = retriever.getMetrics();
        assertEquals(1, metricsByNode.size());
        assertEquals(0, metricsByNode.get(node).size());

    }

    @Test
    public void metrics_from_good_node_are_returned_even_if_another_node_is_dead() {
        var config = nodesConfig("/node0", "/node1");
        Node node0 = new Node(config.node(0));
        Node node1 = new Node(config.node(1));

        wireMockRule.stubFor(get(urlPathEqualTo(config.node(1).metricsPath()))
                                     .willReturn(aResponse().withBody(RESPONSE)));

        ApplicationMetricsRetriever retriever = new ApplicationMetricsRetriever(config);
        retriever.getMetrics();
        retriever.startPollAndWait();
        var metricsByNode = retriever.getMetrics();
        assertEquals(2, metricsByNode.size());
        assertEquals(0, metricsByNode.get(node0).size());
        assertEquals(4, metricsByNode.get(node1).size());
    }

    @Test
    public void an_exception_is_thrown_when_retrieving_times_out() {
        var config = nodesConfig("/node0");
        Node node = new Node(config.node(0));
        wireMockRule.stubFor(get(urlPathEqualTo(config.node(0).metricsPath()))
                                     .willReturn(aResponse()
                                                         .withBody(RESPONSE)
                                                         .withFixedDelay(1000)));

        ApplicationMetricsRetriever retriever = new ApplicationMetricsRetriever(config);
        retriever.setTaskTimeout(Duration.ofMillis(1));
        retriever.setFirstPollTimeout(Duration.ofMillis(1));
        retriever.startPollAndWait();
        assertTrue(retriever.getMetrics().get(node).isEmpty());

    }

    @Test
    public void metrics_can_be_retrieved_after_previous_call_threw_an_exception() {
        var config = nodesConfig("/node0");
        Node node = new Node(config.node(0));

        var delayedStub = wireMockRule.stubFor(get(urlPathEqualTo(config.node(0).metricsPath()))
                                                       .willReturn(aResponse()
                                                                           .withBody(RESPONSE)
                                                                           .withFixedDelay(1000)));

        ApplicationMetricsRetriever retriever = new ApplicationMetricsRetriever(config);
        retriever.setFirstPollTimeout(Duration.ofMillis(1));
        retriever.getMetrics();
        retriever.setTaskTimeout(Duration.ofMillis(1));
        retriever.startPollAndWait();
        assertTrue(retriever.getMetrics().get(node).isEmpty());
        // Verify successful retrieving
        wireMockRule.removeStubMapping(delayedStub);
        verifyRetrievingMetricsFromSingleNode(config, node);
    }

    @Test
    public void first_request_for_a_consumer_waits_for_its_metrics() {
        var config = nodesConfig("/node0", "/node1");
        Node node0 = new Node(config.node(0));
        Node node1 = new Node(config.node(1));

        wireMockRule.stubFor(get(urlPathEqualTo(config.node(0).metricsPath()))
                                     .willReturn(aResponse().withBody(RESPONSE)));
        wireMockRule.stubFor(get(urlPathEqualTo(config.node(1).metricsPath()))
                                     .willReturn(aResponse().withBody(RESPONSE).withFixedDelay(200)));

        ApplicationMetricsRetriever retriever = new ApplicationMetricsRetriever(config);
        retriever.startPollAndWait(); // As done by the handler, before any consumer is known
        var metricsByNode = retriever.getMetrics();
        assertEquals(2, metricsByNode.size());
        assertEquals(4, metricsByNode.get(node0).size());
        assertEquals(4, metricsByNode.get(node1).size());
        retriever.deconstruct();
    }

    @Test
    public void wait_for_first_poll_is_bounded_and_returns_what_is_retrieved_so_far() {
        var config = nodesConfig("/node0", "/node1");
        Node node0 = new Node(config.node(0));
        Node node1 = new Node(config.node(1));

        wireMockRule.stubFor(get(urlPathEqualTo(config.node(0).metricsPath()))
                                     .willReturn(aResponse().withBody(RESPONSE)));
        wireMockRule.stubFor(get(urlPathEqualTo(config.node(1).metricsPath()))
                                     .willReturn(aResponse().withBody(RESPONSE).withFixedDelay(2000)));

        ApplicationMetricsRetriever retriever = new ApplicationMetricsRetriever(config);
        retriever.setFirstPollTimeout(Duration.ofMillis(300));
        retriever.startPollAndWait();
        long start = System.nanoTime();
        var metricsByNode = retriever.getMetrics();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertTrue("Waited " + elapsed, elapsed.compareTo(Duration.ofMillis(1500)) < 0);
        assertEquals(4, metricsByNode.get(node0).size());
        assertEquals(0, metricsByNode.get(node1).size());
        retriever.deconstruct();
    }

    @Test
    public void only_one_request_at_a_time_waits_for_first_poll() throws Exception {
        var config = nodesConfig("/node0");
        wireMockRule.stubFor(get(urlPathEqualTo(config.node(0).metricsPath()))
                                     .willReturn(aResponse().withBody(RESPONSE).withFixedDelay(2000)));

        ApplicationMetricsRetriever retriever = new ApplicationMetricsRetriever(config);
        retriever.setFirstPollTimeout(Duration.ofMillis(1000));
        retriever.startPollAndWait();
        int requests = 3;
        var executor = Executors.newFixedThreadPool(requests);
        var start = new CountDownLatch(1);
        List<Future<Duration>> elapsed = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            ConsumerId consumer = ConsumerId.toConsumerId("consumer-" + i);
            elapsed.add(executor.submit(() -> {
                start.await();
                long before = System.nanoTime();
                retriever.getMetrics(consumer);
                return Duration.ofNanos(System.nanoTime() - before);
            }));
        }
        start.countDown();
        int waited = 0;
        for (var future : elapsed)
            if (future.get().compareTo(Duration.ofMillis(500)) > 0) waited++;
        assertEquals(1, waited);
        executor.shutdown();
        retriever.deconstruct();
    }

    @Test
    public void first_request_returns_when_first_poll_is_done_even_if_a_node_failed() {
        var config = nodesConfig("/node0", "/non-existent");
        Node node0 = new Node(config.node(0));
        Node node1 = new Node(config.node(1));
        wireMockRule.stubFor(get(urlPathEqualTo(config.node(0).metricsPath()))
                                     .willReturn(aResponse().withBody(RESPONSE)));

        ApplicationMetricsRetriever retriever = new ApplicationMetricsRetriever(config);
        retriever.startPollAndWait();
        long start = System.nanoTime();
        var metricsByNode = retriever.getMetrics();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertTrue("Waited " + elapsed, elapsed.compareTo(Duration.ofMillis(2500)) < 0);
        assertEquals(4, metricsByNode.get(node0).size());
        assertEquals(0, metricsByNode.get(node1).size());
        retriever.deconstruct();
    }

    @Test
    public void test_timeout_calculation() {
        assertEquals(MIN_TIMEOUT, timeout(1));
        assertEquals(MIN_TIMEOUT, timeout(20));

        // These values must be updated if the calculation in the timeout method itself is changed.
        assertEquals(Duration.ofSeconds(100), timeout(100));
        assertEquals(Duration.ofSeconds(200), timeout(200));
        assertEquals(MAX_TIMEOUT, timeout(240));
    }

    private MetricsNodesConfig nodesConfig(String... paths) {
        var nodes = Arrays.stream(paths)
                .map(this::nodeConfig)
                .toList();
        return new MetricsNodesConfig.Builder()
                .node(nodes)
                .build();
    }

    private MetricsNodesConfig.Node.Builder nodeConfig(String path) {
        return new MetricsNodesConfig.Node.Builder()
                .role(path)
                .hostname(HOST)
                .metricsPath(path)
                .metricsPort(port);
    }

}
