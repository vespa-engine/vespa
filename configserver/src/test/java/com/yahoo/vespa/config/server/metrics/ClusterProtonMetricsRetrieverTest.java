// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertEquals;

public class ClusterProtonMetricsRetrieverTest {

    @Rule
    public final WireMockRule wireMock = new WireMockRule(options().dynamicPort(), true);

    @Test
    public void testMetricAggregation() throws IOException {
        Collection<URI> host = List.of(URI.create("http://localhost:" + wireMock.port() +"/metrics/v2/values"));

        stubFor(get(urlEqualTo("/metrics/v2/values"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(nodeMetrics())));

        String expectedClusterName = "content/content/0/0";
        Map<String, ProtonMetricsAggregator> aggregatorMap = new ClusterProtonMetricsRetriever().requestMetricsGroupedByCluster(host);

        compareAggregators(
                new ProtonMetricsAggregator()
                .addDocumentReadyCount(1275)
                .addDocumentActiveCount(1275)
                .addDocumentTotalCount(1275)
                .addDocumentDiskUsage(14781856)
                .addResourceDiskUsageAverage(0.0009083386306)
                .addResourceMemoryUsageAverage(0.0183488434436),
                aggregatorMap.get(expectedClusterName)
        );

        wireMock.stop();
    }

    private String nodeMetrics() throws IOException {
        return Files.readString(Path.of("src/test/resources/metrics/node_metrics"));
    }

    // Same tolerance value as used internally in MetricsAggregator.isZero
    private static final double metricsTolerance = 0.001;

    private void compareAggregators(ProtonMetricsAggregator expected, ProtonMetricsAggregator actual) {
        assertEquals(expected.aggregateDocumentDiskUsage(), actual.aggregateDocumentDiskUsage(), metricsTolerance);
    }

}
