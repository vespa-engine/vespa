// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric;

import ai.vespa.metricsproxy.service.VespaService;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Format metrics as required by users of the "getMetricsById" rpc method.
 *
 * @author Unknown
 */
public class MetricsFormatter {

    private final boolean includeServiceName;
    private final boolean isSystemMetric;
    private final DecimalFormat df = new DecimalFormat("0.000", new DecimalFormatSymbols(Locale.ENGLISH));

    public MetricsFormatter(boolean includeServiceName, boolean isSystemMetric) {
        this.includeServiceName = includeServiceName;
        this.isSystemMetric = isSystemMetric;
    }

    public String format(VespaService service, String name, Number value) {
        StringBuilder sb = new StringBuilder();

        if (includeServiceName) {
            sb.append(service.getServiceName()).append(".");
        }

        if (isSystemMetric)
            sb.append(toSystemServiceId(service.getConfigId()));
        else
            sb.append(toServiceId(service.getConfigId()));

        sb.append(".")
                .append(formatMetricName(name))
                .append("=");

        if (value instanceof Double) {
            sb.append(df.format(value.doubleValue()));
        } else {
            sb.append(value.toString());
        }

        return sb.toString();
    }

    private static String formatMetricName(String name) {
        name = name.replaceAll("\"", "");
        name = name.replaceAll("\\.", "_");
        return name;
    }

    // E.g. container/default.1 ->  'container.default.1'
    private static String toServiceId(String configId) {
        return "'" + configId.replace("/", ".") + "'";
    }

    // E.g. container/default.1 ->  container.'default.1'
    private static String toSystemServiceId(String configId) {
        String name = configId.replace("/", ".");
        name = name.replaceFirst("\\.", ".'") + "'";
        return name;
    }

}
