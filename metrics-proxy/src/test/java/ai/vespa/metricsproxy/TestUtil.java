// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy;

import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.core.VespaMetrics;
import ai.vespa.metricsproxy.metric.ExternalMetrics;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.MetricDimensionMapping;
import ai.vespa.metricsproxy.metric.dimensions.MetricDimensionMappingConfig;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.service.VespaServices;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * @author gjoranv
 */
public class TestUtil {

    public static MetricsManager createMetricsManager(VespaServices vespaServices,
                                                      MetricsConsumers consumers,
                                                      ApplicationDimensions applicationDimensions,
                                                      NodeDimensions nodeDimensions) {
        VespaMetrics metrics = new VespaMetrics(consumers);
        return new MetricsManager(vespaServices, metrics, new ExternalMetrics(consumers, standardDimensionMapping()),
                                  applicationDimensions, nodeDimensions);
    }

    /**
     * The metric-to-dimension mapping that config-model generates by default: host_life gets osVersion
     * in addition to host/parentHostname; services not listed keep only host/parentHostname.
     */
    public static MetricDimensionMapping standardDimensionMapping() {
        return new MetricDimensionMapping(new MetricDimensionMappingConfig.Builder()
                .defaultDimension("host")
                .defaultDimension("parentHostname")
                .mapping(new MetricDimensionMappingConfig.Mapping.Builder()
                        .service("host_life")
                        .dimension("host")
                        .dimension("parentHostname")
                        .dimension("osVersion"))
                .build());
    }

    public static String getFileContents(String filename) {
        InputStream in = TestUtil.class.getClassLoader().getResourceAsStream(filename);
        if (in == null) {
            throw new RuntimeException("File not found: " + filename);
        }
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
    }

}
