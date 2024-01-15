// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.http.server.jetty.ResponseMetricAggregator.StatisticsEntry;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author ollivir
 * @author bjorncs
 */
public class ResponseMetricAggregatorTest {

    private final List<String> monitoringPaths = List.of("/status.html");
    private final List<String> searchPaths = List.of("/search");
    private final ResponseMetricAggregator collector = new ResponseMetricAggregator(monitoringPaths, searchPaths, Set.of(), false);

    @BeforeEach
    public void initializeCollector() {
        collector.takeStatistics();
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

        Response resp = mock(Response.class);
        when(resp.getCommittedMetaData())
                .thenReturn(new MetaData.Response(HttpVersion.HTTP_1_1, responseCode, HttpFields.EMPTY));
        Request req = mock(Request.class);
        when(req.getResponse()).thenReturn(resp);
        when(req.getMethod()).thenReturn(httpMethod);
        when(req.getScheme()).thenReturn(scheme);
        when(req.getRequestURI()).thenReturn(path);
        when(req.getAttribute(ResponseMetricAggregator.requestTypeAttribute)).thenReturn(explicitRequestType);
        when(req.getProtocol()).thenReturn(HttpVersion.HTTP_1_1.asString());

        collector.onResponseCommit(req);
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
                .orElseThrow(() -> new AssertionError(String.format("Not matching entry in result (scheme=%s, method=%s, name=%s, type=%s)", scheme, method, name, requestType)));
        assertThat(value, equalTo(expectedValue));
    }

}
