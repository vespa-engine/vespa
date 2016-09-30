// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.restapi;

import com.google.inject.Inject;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects last value from all the previously declared counters/gauges and genereates a map
 * structure that can be converted to secret-agent JSON message
 *
 * @author valerijf
 */
public class SecretAgentHandler {
    private static final String applicationName = "docker";
    private final MetricReceiverWrapper metricReceiver;
    private final Map<String, Object> dimensions;

    @Inject
    public SecretAgentHandler(MetricReceiverWrapper metricReceiver) {
        this.metricReceiver = metricReceiver;
        dimensions = new LinkedHashMap<>();
        dimensions.put("host", com.yahoo.net.HostName.getLocalhost());
    }

    public Map<String, Object> getNodeAdminSecretAgentReport() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        for (String metricName : metricReceiver.getMetricNames()) {
            metrics.put(metricName, metricReceiver.getMetricByName(metricName).getValue());
        }

        return generateSecretAgentReport(dimensions, metrics);
    }

    public static Map<String, Object> generateSecretAgentReport(Map<String, Object> dimensions, Map<String, Object> metrics) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("application", applicationName);
        report.put("timestamp", System.currentTimeMillis() / 1000);
        report.put("dimensions", dimensions);
        report.put("metrics", metrics);

        return report;
    }
}
