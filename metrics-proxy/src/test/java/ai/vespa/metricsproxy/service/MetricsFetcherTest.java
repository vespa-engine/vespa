// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.TestUtil;
import ai.vespa.metricsproxy.metric.Metrics;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 */
public class MetricsFetcherTest {

    private static int port = 9;  //port number is not used in this test

    @Test
    public void testStateFormatMetricsParse() {
        String jsonData = TestUtil.getFileContents("metrics-state.json");
        RemoteMetricsFetcher fetcher = new RemoteMetricsFetcher(new DummyService(0, "dummy/id/0"), port);
        Metrics metrics = fetcher.createMetrics(jsonData, 0);
        assertThat(metrics.size(), is(10));
        assertThat(metrics.getMetric("query_hits.count").getValue().intValue(), is(28));
        assertThat(metrics.getMetric("queries.rate").getValue().doubleValue(), is(0.4667));
        assertThat(metrics.getTimeStamp(), is(1334134700L));
    }

    @Test
    public void testEmptyJson() {
        String  jsonData = "{}";
        RemoteMetricsFetcher fetcher = new RemoteMetricsFetcher(new DummyService(0, "dummy/id/0"), port);
        Metrics metrics = fetcher.createMetrics(jsonData, 0);
        assertThat("Wrong number of metrics", metrics.size(), is(0));
    }

    @Test
    public void testErrors() {
        String jsonData;
        Metrics metrics;

        RemoteMetricsFetcher fetcher = new RemoteMetricsFetcher(new DummyService(0, "dummy/id/0"), port);

        jsonData = "";
        metrics = fetcher.createMetrics(jsonData, 0);
        assertThat("Wrong number of metrics", metrics.size(), is(0));

        jsonData = "{\n" +
                "\"status\" : {\n" +
                "  \"code\" : \"up\",\n" +
                "  \"message\" : \"Everything ok here\"\n" +
                "}\n" +
                "}";
        metrics = fetcher.createMetrics(jsonData, 0);
        assertThat("Wrong number of metrics", metrics.size(), is(0));

        jsonData = "{\n" +
                "\"status\" : {\n" +
                "  \"code\" : \"up\",\n" +
                "  \"message\" : \"Everything ok here\"\n" +
                "},\n" +
                "\"metrics\" : {\n" +
                "  \"snapshot\" : {\n" +
                "    \"from\" : 1334134640.089,\n" +
                "    \"to\" : 1334134700.088\n" +
                "  },\n" +
                "  \"values\" : [\n" +
                "   {\n" +
                "     \"name\" : \"queries\",\n" +
                "     \"description\" : \"Number of queries executed during snapshot interval\",\n" +
                "     \"values\" : {\n" +
                "       \"count\" : null,\n" +
                "       \"rate\" : 0.4667\n" +
                "     },\n" +
                "     \"dimensions\" : {\n" +
                "      \"searcherid\" : \"x\"\n" +
                "     }\n" +
                "   }\n" + "" +
                " ]\n" +
                "}\n" +
                "}";

        metrics = fetcher.createMetrics(jsonData, 0);
        assertThat("Wrong number of metrics", metrics.size(), is(0));
    }
}
