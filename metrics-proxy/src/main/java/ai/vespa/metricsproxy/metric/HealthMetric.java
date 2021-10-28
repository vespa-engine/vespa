// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric;

import ai.vespa.metricsproxy.metric.model.StatusCode;

import static ai.vespa.metricsproxy.metric.model.StatusCode.DOWN;
import static ai.vespa.metricsproxy.metric.model.StatusCode.UNKNOWN;
import static ai.vespa.metricsproxy.metric.model.StatusCode.UP;

/**
 * TODO: Use MetricsPacket instead of this class.
 *
 * @author Jo Kristian Bergum
 */
public class HealthMetric {
    private final String message;
    private final StatusCode status;
    private final boolean isAlive;

    private HealthMetric(StatusCode status, String message, boolean isAlive) {
        this.message = message;
        this.status = status;
        this.isAlive = isAlive;
    }

    public static HealthMetric get(String status, String message) {
        if (message == null) message = "";
        var statusCode = StatusCode.fromString(status);
        return new HealthMetric(statusCode, message, statusCode == UP);
    }

    public static HealthMetric getDown(String message) {
        return new HealthMetric(DOWN, message, false);
    }

    public static HealthMetric getUnknown(String message) {
        return new HealthMetric(UNKNOWN, message, false);
    }

    public static HealthMetric getOk(String message) {
        return new HealthMetric(UP, message, true);
    }

    public String getMessage() {
        return this.message;
    }

    public StatusCode getStatus() {
        return this.status;
    }

    public boolean isOk() {
        return this.isAlive;
    }
}
