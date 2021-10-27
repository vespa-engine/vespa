// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model;

/**
 * Status code for a Vespa service.
 *
 * @author gjoranv
 */
public enum StatusCode {

    UP(0, "up"),
    DOWN(1, "down"),
    UNKNOWN(2, "unknown");

    public final int code;
    public final String status;

    StatusCode(int code, String status) {
        this.code = code;
        this.status = status;
    }

    public static StatusCode fromString(String statusString) {
        if ("ok".equalsIgnoreCase(statusString)) return UP;
        try {
            return valueOf(statusString.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return UNKNOWN;
        }
    }

}
