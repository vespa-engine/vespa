// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import junit.framework.AssertionFailedError;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertEquals;


/**
 * @author olaa
 */
public class ClusterDeploymentMetricsRetrieverTest {

    @Rule
    public final WireMockRule wireMock = new WireMockRule(options().dynamicPort(), true);

    @Test
    public void testMetricAggregation() throws IOException {
        List<URI> hosts = Stream.of(1, 2, 3, 4)
                .map(item -> URI.create("http://localhost:" + wireMock.port() + "/" + item))
                .toList();

        stubFor(get(urlEqualTo("/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(contentMetrics())));

        stubFor(get(urlEqualTo("/2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(contentMetrics())));

        stubFor(get(urlEqualTo("/3"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(containerMetrics())));

        stubFor(get(urlEqualTo("/4"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(clustercontrollerMetrics())));

        ClusterInfo expectedContentCluster = new ClusterInfo("content_cluster_id", "content");
        ClusterInfo expectedContainerCluster = new ClusterInfo("container_cluster_id", "container");

        Map<ClusterInfo, DeploymentMetricsAggregator> aggregatorMap = new ClusterDeploymentMetricsRetriever().requestMetricsGroupedByCluster(hosts);
        assertEquals(Set.of(expectedContainerCluster, expectedContentCluster), aggregatorMap.keySet());

        compareAggregators(
                new DeploymentMetricsAggregator()
                        .addDocumentCount(6000.0)
                        .addMemoryUsage(0.89074, 0.8)
                        .addDiskUsage(0.83517, 0.75),
                aggregatorMap.get(expectedContentCluster)
        );

        compareAggregators(
                new DeploymentMetricsAggregator()
                        .addContainerLatency(3000, 43)
                        .addContainerLatency(2000, 0)
                        .addQrLatency(3000, 43)
                        .addFeedLatency(3000, 43),
                aggregatorMap.get(expectedContainerCluster)

        );
        wireMock.stop();
    }

    private String containerMetrics() throws IOException {
        return Files.readString(Path.of("src/test/resources/metrics/container_metrics.json"));
    }

    private String contentMetrics() throws IOException {
        return Files.readString(Path.of("src/test/resources/metrics/content_metrics.json"));
    }

    private String clustercontrollerMetrics() throws IOException {
        return Files.readString(Path.of("src/test/resources/metrics/clustercontroller_metrics.json"));
    }

    // Same tolerance value as used internally in MetricsAggregator.isZero
    private static final double metricsTolerance = 0.001;

    private void compareAggregators(DeploymentMetricsAggregator expected, DeploymentMetricsAggregator actual) {
        BiConsumer<Double, Double> assertDoubles = (a, b) -> assertEquals(a, b, metricsTolerance);

        compareOptionals(expected.aggregateDocumentCount(), actual.aggregateDocumentCount(), assertDoubles);
        compareOptionals(expected.aggregateQueryRate(), actual.aggregateQueryRate(), assertDoubles);
        compareOptionals(expected.aggregateFeedRate(), actual.aggregateFeedRate(), assertDoubles);
        compareOptionals(expected.aggregateQueryLatency(), actual.aggregateQueryLatency(), assertDoubles);
        compareOptionals(expected.aggregateFeedLatency(), actual.aggregateFeedLatency(), assertDoubles);
        compareOptionals(expected.diskUsage(), actual.diskUsage(), (a, b) -> assertDoubles.accept(a.util(), b.util()));
        compareOptionals(expected.diskUsage(), actual.diskUsage(), (a, b) -> assertDoubles.accept(a.feedBlockLimit(), b.feedBlockLimit()));
        compareOptionals(expected.memoryUsage(), actual.memoryUsage(), (a, b) -> assertDoubles.accept(a.util(), b.util()));
        compareOptionals(expected.memoryUsage(), actual.memoryUsage(), (a, b) -> assertDoubles.accept(a.feedBlockLimit(), b.feedBlockLimit()));
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static <T> void compareOptionals(Optional<T> a, Optional<T> b, BiConsumer<T, T> comparer) {
        if (a.isPresent() != b.isPresent()) throw new AssertionFailedError("Both optionals are not present: " + a + ", " + b);
        a.ifPresent(x -> b.ifPresent(y -> comparer.accept(x, y)));
    }
}
