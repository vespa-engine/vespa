/*
* Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.metric;

/**
 * @author Jo Kristian Bergum
 */
public class HealthMetric {
    private final String message;
    private final String status;
    private final boolean isAlive;

    private HealthMetric(String status, String message, boolean isAlive) {
        this.message = message;
        this.status = status;
        this.isAlive = isAlive;
    }

    public static HealthMetric get(String status, String message) {
        if (status == null) {
            status = "";
        }
        if (message == null) {
            message = "";
        }
        status = status.toLowerCase();

        if (status.equals("up") || status.equals("ok")) {
            return new HealthMetric(status, message, true);
        } else {
            return new HealthMetric(status, message, false);
        }
    }

    public static HealthMetric getFailed(String message) {
        return new HealthMetric("down", message, false);
    }

    public static HealthMetric getOk(String message) {
        return new HealthMetric("up", message, true);
    }

    public String getMessage() {
        return this.message;
    }

    public String getStatus() {
        return this.status;
    }

    public boolean isOk() {
        return this.isAlive;
    }
}
