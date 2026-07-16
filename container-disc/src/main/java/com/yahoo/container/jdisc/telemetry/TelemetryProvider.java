// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.telemetry;

import ai.vespa.telemetry.TelemetryConfig;
import ai.vespa.telemetry.api.NoopTelemetry;
import ai.vespa.telemetry.api.Telemetry;
import ai.vespa.telemetry.otel.OtelTelemetry;
import com.yahoo.component.annotation.Inject;
import com.yahoo.container.di.componentgraph.Provider;

/**
 * Provides the container's Telemetry instance. Disabled by default (TelemetryConfig.enabled=false):
 * hands out the no-op. The OTel-backed implementation class is referenced ONLY inside the enabled
 * branch so its classes never load in disabled containers or test harnesses (D10).
 */
public class TelemetryProvider implements Provider<Telemetry> {

    private final Telemetry telemetry;

    @Inject
    public TelemetryProvider(TelemetryConfig config) {
        this.telemetry = config.enabled()
                ? OtelTelemetry.create(config)
                : NoopTelemetry.INSTANCE;
    }

    @Override public Telemetry get() { return telemetry; }

    @Override public void deconstruct() {
        if (telemetry instanceof AutoCloseable c) {
            try { c.close(); } catch (Exception ignored) { }  // best-effort flush on reconfig
        }
    }
}
