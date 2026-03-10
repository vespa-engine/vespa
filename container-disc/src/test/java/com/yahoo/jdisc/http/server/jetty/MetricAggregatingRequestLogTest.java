// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import ai.vespa.metrics.ContainerMetrics;
import com.yahoo.jdisc.http.server.jetty.MetricAggregatingRequestLog.StatisticsEntry;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.text.Text;
import org.eclipse.jetty.http.HttpVersion;

import static com.yahoo.test.JunitCompat.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * @author ollivir
 * @author bjorncs
 */
public class MetricAggregatingRequestLogTest {

    private final List<String> monitoringPaths = List.of("/status.html");
    private final List<String> searchPaths = List.of("/search");
    private final MockMetric metric = new MockMetric();
    private final MetricAggregatingRequestLog collector = new MetricAggregatingRequestLog(monitoringPaths, searchPaths, List.of(), false, metric, new MetricReceiver.MockReceiver());

    @BeforeEach
    public void initializeCollector() {
        collector.takeStatistics();
    }

    @Test
    void latency_is_recorded() {
        testRequest("http", 200, "GET");
        assertTrue("Latency metric is recorded", metric.metrics().containsKey(MetricDefinitions.LATENCY));
        var metricSnapshot = metric.metrics().get(MetricDefinitions.LATENCY);
        assertEquals(1, metricSnapshot.size());
        var latencySample = metricSnapshot.entrySet().iterator().next();
        assertEquals(200L, latencySample.getKey().get(MetricDefinitions.STATUS_CODE_DIMENSION));
        assertEquals(0.0, latencySample.getValue());
    }

    @Test
    void statistics_are_aggregated_by_category() {
        testRequest("http", 300, "GET");
        testRequest("http", 301, "GET");
        testRequest("http", 200, "GET");

        var stats = collector.takeStatistics();
        assertStatisticsEntry(stats, "http", "GET", MetricDefinitions.RESPONSES_2XX, "read", 200, 1L);
        assertStatisticsEntry(stats, "http", "GET", MetricDefinitions.RESPONSES_3XX, "read", 301, 1L);
        assertStatisticsEntry(stats, "http", "GET", MetricDefinitions.RESPONSES_3XX, "read", 300, 1L);
    }

    @Test
    void statistics_are_grouped_by_http_method_and_scheme() {
        testRequest("http", 200, "GET");
        testRequest("http", 200, "PUT");
        testRequest("http", 200, "POST");
        testRequest("http", 200, "POST");
        testRequest("http", 404, "GET");
        testRequest("https", 404, "GET");
        testRequest("https", 200, "POST");
        testRequest("https", 200, "POST");
        testRequest("https", 200, "POST");
        testRequest("https", 200, "POST");

        var stats = collector.takeStatistics();
        assertStatisticsEntry(stats, "http", "GET", MetricDefinitions.RESPONSES_2XX, "read", 200, 1L);
        assertStatisticsEntry(stats, "http", "GET", MetricDefinitions.RESPONSES_4XX, "read", 404, 1L);
        assertStatisticsEntry(stats, "http", "PUT", MetricDefinitions.RESPONSES_2XX, "write", 200, 1L);
        assertStatisticsEntry(stats, "http", "POST", MetricDefinitions.RESPONSES_2XX, "write", 200, 2L);
        assertStatisticsEntry(stats, "https", "GET", MetricDefinitions.RESPONSES_4XX, "read", 404, 1L);
        assertStatisticsEntry(stats, "https", "POST", MetricDefinitions.RESPONSES_2XX, "write", 200, 4L);
    }

    @Test
    void statistics_include_grouped_and_single_statuscodes() {
        testRequest("http", 401, "GET");
        testRequest("http", 404, "GET");
        testRequest("http", 403, "GET");

        var stats = collector.takeStatistics();
        assertStatisticsEntry(stats, "http", "GET", MetricDefinitions.RESPONSES_4XX, "read", 401, 1L);
        assertStatisticsEntry(stats, "http", "GET", MetricDefinitions.RESPONSES_4XX, "read", 403, 1L);
        assertStatisticsEntry(stats, "http", "GET", MetricDefinitions.RESPONSES_4XX, "read", 404, 1L);

    }

    @Test
    void retrieving_statistics_resets_the_counters() {
        testRequest("http", 200, "GET");
        testRequest("http", 200, "GET");

        var stats = collector.takeStatistics();
        assertStatisticsEntry(stats, "http", "GET", MetricDefinitions.RESPONSES_2XX, "read", 200, 2L);

        testRequest("http", 200, "GET");

        stats = collector.takeStatistics();
        assertStatisticsEntry(stats, "http", "GET", MetricDefinitions.RESPONSES_2XX, "read", 200, 1L);
    }

    @Test
    void statistics_include_request_type_dimension() {
        testRequest("http", 200, "GET", "/search");
        testRequest("http", 200, "POST", "/search");
        testRequest("http", 200, "POST", "/feed");
        testRequest("http", 200, "GET", "/status.html?foo=bar");

        var stats = collector.takeStatistics();
        assertStatisticsEntry(stats, "http", "GET", MetricDefinitions.RESPONSES_2XX, "monitoring", 200, 1L);
        assertStatisticsEntry(stats, "http", "GET", MetricDefinitions.RESPONSES_2XX, "read", 200, 1L);
        assertStatisticsEntry(stats, "http", "POST", MetricDefinitions.RESPONSES_2XX, "read", 200, 1L);
        assertStatisticsEntry(stats, "http", "POST", MetricDefinitions.RESPONSES_2XX, "write", 200, 1L);

        testRequest("http", 200, "GET");

        stats = collector.takeStatistics();
        assertStatisticsEntry(stats, "http", "GET", MetricDefinitions.RESPONSES_2XX, "read", 200, 1L);
    }

    @Test
    void request_type_can_be_set_explicitly() {
        testRequest("http", 200, "GET", "/search", com.yahoo.jdisc.Request.RequestType.WRITE);

        var stats = collector.takeStatistics();
        assertStatisticsEntry(stats, "http", "GET", MetricDefinitions.RESPONSES_2XX, "write", 200, 1L);
    }

    private void testRequest(String scheme, int responseCode, String httpMethod) {
        testRequest(scheme, responseCode, httpMethod, "foo/bar");
    }

    private void testRequest(String scheme, int responseCode, String httpMethod, String path) {
        testRequest(scheme, responseCode, httpMethod, path, null);
    }

    private void testRequest(String scheme, int responseCode, String httpMethod, String path,
                                com.yahoo.jdisc.Request.RequestType explicitRequestType) {
        var builder = JettyMockRequestBuilder.newBuilder()
                .method(httpMethod)
                .uri(scheme, "localhost", 8080, path, null)
                .protocol(HttpVersion.HTTP_1_1.asString());
        if (explicitRequestType != null)
            builder.attribute(MetricAggregatingRequestLog.requestTypeAttribute, explicitRequestType);
        collector.onResponse(builder.build(), responseCode);
    }

    private static void assertStatisticsEntry(List<StatisticsEntry> result, String scheme, String method, String name,
                                              String requestType, int statusCode, long expectedValue) {
        long value = result.stream()
                .filter(entry -> entry.dimensions.method.equals(method)
                        && entry.dimensions.scheme.equals(scheme)
                        && entry.name.equals(name)
                        && entry.dimensions.requestType.equals(requestType)
                        && entry.dimensions.statusCode == statusCode)
                .mapToLong(entry -> entry.value)
                .reduce(Long::sum)
                .orElseThrow(() -> new AssertionError(Text.format("Not matching entry in result (scheme=%s, method=%s, name=%s, type=%s)", scheme, method, name, requestType)));
        assertEquals(expectedValue, value);
    }

}
