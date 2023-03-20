// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertEquals;

public class ClusterSearchNodeMetricsRetrieverTest {

    @Rule
    public final WireMockRule wireMock = new WireMockRule(options().dynamicPort(), true);


    @Test
    public void testMetricAggregation() throws IOException {
        List<URI> hosts = Stream.of(1, 2)
                .map(item -> URI.create("http://localhost:" + wireMock.port() + "/metrics" + item + "/v2/values"))
                .toList();

        stubFor(get(urlEqualTo("/metrics1/v2/values"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(nodeMetrics("_1"))));

        stubFor(get(urlEqualTo("/metrics2/v2/values"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(nodeMetrics("_2"))));

        String expectedClusterNameContent = "content/content/0/0";
        String expectedClusterNameMusic = "content/music/0/0";
        Map<String, SearchNodeMetricsAggregator> aggregatorMap = new ClusterSearchNodeMetricsRetriever().requestMetricsGroupedByCluster(hosts);

        compareAggregators(
                new SearchNodeMetricsAggregator()
                .addDocumentReadyCount(1275)
                .addDocumentActiveCount(1275)
                .addDocumentTotalCount(1275)
                .addDocumentDiskUsage(14781856)
                .addResourceDiskUsageAverage(0.0009083386306)
                .addResourceMemoryUsageAverage(0.0183488434436),
                aggregatorMap.get(expectedClusterNameContent)
        );

        compareAggregators(
                new SearchNodeMetricsAggregator()
                .addDocumentReadyCount(3008)
                .addDocumentActiveCount(3008)
                .addDocumentTotalCount(3008)
                .addDocumentDiskUsage(331157)
                .addResourceDiskUsageAverage(0.0000152263558)
                .addResourceMemoryUsageAverage(0.0156505524171),
                aggregatorMap.get(expectedClusterNameMusic)
        );

        wireMock.stop();
    }

    private String nodeMetrics(String extension) throws IOException {
        return Files.readString(Path.of("src/test/resources/metrics/node_metrics" + extension + ".json"));
    }

    // Same tolerance value as used internally in MetricsAggregator.isZero
    private static final double metricsTolerance = 0.001;

    private void compareAggregators(SearchNodeMetricsAggregator expected, SearchNodeMetricsAggregator actual) {
        assertEquals(expected.aggregateDocumentDiskUsage(), actual.aggregateDocumentDiskUsage(), metricsTolerance);
    }

}
