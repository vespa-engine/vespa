// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy;

import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.core.MetricsManager;
import ai.vespa.metricsproxy.core.VespaMetrics;
import ai.vespa.metricsproxy.metric.ExternalMetrics;
import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.service.VespaServices;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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
        return new MetricsManager(vespaServices, metrics, new ExternalMetrics(consumers),
                                  applicationDimensions, nodeDimensions);
    }

    public static String getFileContents(String filename) {
        InputStream in = TestUtil.class.getClassLoader().getResourceAsStream(filename);
        if (in == null) {
            throw new RuntimeException("File not found: " + filename);
        }
        return new BufferedReader(new InputStreamReader(in)).lines().collect(Collectors.joining("\n"));
    }

}
