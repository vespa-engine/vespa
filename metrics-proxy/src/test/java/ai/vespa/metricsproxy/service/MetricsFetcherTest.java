// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.TestUtil;
import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.Metrics;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;

import static ai.vespa.metricsproxy.metric.model.MetricId.toMetricId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 */
public class MetricsFetcherTest {

    private static final int port = 9;  //port number is not used in this test

    private static class MetricsConsumer implements MetricsParser.Consumer {
        Metrics metrics = new Metrics();
        @Override
        public void consume(Metric metric) {
            metrics.add(metric);
        }
    }
    Metrics fetch(String data) throws IOException {
        RemoteMetricsFetcher fetcher = new RemoteMetricsFetcher(new DummyService(0, "dummy/id/0"), port);
        MetricsConsumer consumer = new MetricsConsumer();
        fetcher.createMetrics(data, consumer, 0);
        return consumer.metrics;
    }

    @Test
    public void testStateFormatMetricsParse() throws IOException {
        String jsonData = TestUtil.getFileContents("metrics-state.json");
        Metrics metrics = fetch(jsonData);
        assertEquals(10, metrics.size());
        assertEquals(28L, getMetric("query_hits.count", metrics).getValue());
        assertEquals(0.4667, getMetric("queries.rate", metrics).getValue());
        assertEquals(Instant.ofEpochSecond(1334134700L), metrics.getTimeStamp());
    }

    @Test
    public void testEmptyJson() throws IOException {
        String  jsonData = "{}";
        Metrics metrics = fetch(jsonData);
        assertEquals(0, metrics.size());
    }

    @Test
    public void testSkippingNullDimensions() throws IOException {
        String jsonData =
                "{\"status\" : {\"code\" : \"up\",\"message\" : \"Everything ok here\"}," +
                "\"metrics\" : {\"snapshot\" : {\"from\" : 1334134640.089,\"to\" : 1334134700.088" + "  }," +
                "\"values\" : [" +
                "{" +
                "      \"name\" : \"some.bogus.metric\"," +
                "      \"values\" : {" +
                "        \"count\" : 12," +
                "        \"rate\" : 0.2" +
                "      }," +
                "      \"dimensions\" : {" +
                "        \"version\" : null" +
                "      }" +
                "    }" +
                "]}}";

        Metrics metrics = fetch(jsonData);
        assertEquals(2, metrics.size());
        assertTrue(metrics.list().get(0).getDimensions().isEmpty());
        assertTrue(metrics.list().get(1).getDimensions().isEmpty());
    }

    @Test
    public void testErrors() throws IOException {
        String jsonData;
        Metrics metrics = null;

        jsonData = "";
        try {
            metrics = fetch(jsonData);
            fail("Should have an IOException instead");
        } catch (IOException e) {
            assertEquals("Expected start of object, got null", e.getMessage());
        }
        assertNull(metrics);

        jsonData = "{\n" +
                "\"status\" : {\n" +
                "  \"code\" : \"up\",\n" +
                "  \"message\" : \"Everything ok here\"\n" +
                "}\n" +
                "}";
        metrics = fetch(jsonData);
        assertEquals(0, metrics.size());

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

        metrics = null;
        try {
            metrics = fetch(jsonData);
            fail("Should have an IOException instead");
        } catch (IllegalArgumentException e) {
            assertEquals("Value for aggregator 'count' is not a number", e.getMessage());
        }
        assertNull(metrics);
    }

    public Metric getMetric(String metric, Metrics metrics) {
        for (Metric m: metrics.list()) {
            if (m.getName().equals(toMetricId(metric)))
                return m;
        }
        return null;
    }

}
