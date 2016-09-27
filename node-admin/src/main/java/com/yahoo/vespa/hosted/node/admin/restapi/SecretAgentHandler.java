package com.yahoo.vespa.hosted.node.admin.restapi;

import com.google.inject.Inject;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author valerijf
 */
public class SecretAgentHandler {
    private final String applicationName = "node-admin";
    private final String hostName;
    private final MetricReceiverWrapper metricReceiver;

    @Inject
    public SecretAgentHandler(MetricReceiverWrapper metricReceiver) {
        this.hostName = com.yahoo.net.HostName.getLocalhost();
        this.metricReceiver = metricReceiver;
    }

    public Map<String, Object> getSecretAgentReport() {
        Map<String, String> dimensions = new LinkedHashMap<>();
        dimensions.put("host", hostName);

        Map<String, Number> metrics = new LinkedHashMap<>();
        for (String metricName : metricReceiver.getMetricNames()) {
            metrics.put(metricName, metricReceiver.getMetricByName(metricName).getValue());
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("application", applicationName);
        report.put("timestamp", System.currentTimeMillis() / 1000.);
        report.put("dimensions", dimensions);
        report.put("metrics", metrics);

        return report;
    }
}
