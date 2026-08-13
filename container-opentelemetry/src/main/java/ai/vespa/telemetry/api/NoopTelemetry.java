// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.telemetry.api;

import ai.vespa.telemetry.api.trace.ScopedTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapPropagator;

/**
 * Disabled path: backed by OpenTelemetry.noop() — non-recording spans, no SDK, no threads.
 *
 * <p>{@code noop} is declared before {@code INSTANCE} on purpose: static initializers run in
 * textual order, so this ordering keeps {@code noop} assigned before any instance exists, and
 * stays correct if the constructor ever stops being empty.</p>
 *
 * @author onur
 */
public final class NoopTelemetry implements Telemetry {

    private static final OpenTelemetry noop = OpenTelemetry.noop();
    public static final Telemetry INSTANCE = new NoopTelemetry();

    private NoopTelemetry() { }

    @Override public ScopedTracer tracer(String scope) { return new ScopedTracer(noop.getTracer(scope)); }
    @Override public TextMapPropagator textMapPropagator() { return noop.getPropagators().getTextMapPropagator(); }
}
