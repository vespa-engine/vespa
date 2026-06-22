// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * Settings for Vespa's OpenTelemetry SDK (tracing), controlled by the {@code opentelemetry-sdk}
 * feature flag and produced into the container's telemetry config.
 *
 * @author onur
 */
public interface OpenTelemetryConfiguration {

    boolean enabled();
    double samplingRatio();

    /** The default, disabled configuration: produces a no-op OpenTelemetry. */
    static OpenTelemetryConfiguration disabled() {
        return new OpenTelemetryConfiguration() {
            @Override public boolean enabled() { return false; }
            @Override public double samplingRatio() { return 1.0; }
        };
    }

}
