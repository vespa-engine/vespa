// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.processing.MetricsProcessor;

import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;

/**
 * Adds a 'hostname' dimension to all metrics.
 *
 * @author olaa
 */
public class HostnameDimensionProcessor implements MetricsProcessor {

    static final String HOSTNAME_DIMENSION_NAME = "hostname";
    private final String hostname;

    public HostnameDimensionProcessor(String hostname) {
        this.hostname = hostname;
    }

    @Override
    public void process(MetricsPacket.Builder builder) {
        builder.putDimension(toDimensionId(HOSTNAME_DIMENSION_NAME), hostname);
    }

}
