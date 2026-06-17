// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.telemetry;

import ai.vespa.telemetry.TelemetryConfig;
import com.yahoo.component.annotation.Inject;
import com.yahoo.container.di.componentgraph.Provider;
import io.opentelemetry.api.OpenTelemetry;

/**
 * Provides the container's {@link OpenTelemetry} instance as an injectable component.
 *
 * <p>Disabled by default: when {@code telemetry.enabled=false} this hands out {@link OpenTelemetry#noop()},
 * which constructs no SDK, starts no exporter threads, opens no connections and produces no telemetry.
 * The real SDK is built only when explicitly enabled, so the component is safe to ship disabled and roll
 * out gradually via config.</p>
 *
 * <p>This class deliberately references only the OpenTelemetry <em>API</em>. The SDK is built in
 * {@link OpenTelemetrySdkBuilder}, which is loaded only when tracing is enabled. That keeps the SDK classes
 * off the (public) container classpath: the in-process container only needs the OTel API to instantiate this
 * provider in its disabled, no-op form.</p>
 *
 * @author onur
 */
public class OpenTelemetryProvider implements Provider<OpenTelemetry> {

    private final OpenTelemetry openTelemetry;

    @Inject
    public OpenTelemetryProvider(TelemetryConfig config) {
        this.openTelemetry = config.enabled()
                ? OpenTelemetrySdkBuilder.build(config)   // loads SDK classes only when enabled
                : OpenTelemetry.noop();
    }

    @Override
    public OpenTelemetry get() { return openTelemetry; }

    @Override
    public void deconstruct() {
        // The real SDK implements AutoCloseable; closing flushes queued spans and stops exporter threads on
        // reconfiguration. The no-op instance is not AutoCloseable, so nothing happens when disabled.
        if (openTelemetry instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // best-effort shutdown
            }
        }
    }

}
