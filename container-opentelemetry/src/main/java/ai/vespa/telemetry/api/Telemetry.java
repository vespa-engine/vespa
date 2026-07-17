// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.telemetry.api;

import ai.vespa.telemetry.api.trace.ScopedTracer;
import io.opentelemetry.context.propagation.TextMapPropagator;

/**
 * Entry point for Vespa-internal telemetry. Inject this into any container component.
 *
 * <p>RESERVED TYPE NAMES: platform code must never inject io.opentelemetry.api.OpenTelemetry or
 * io.opentelemetry.api.trace.TracerProvider — those type names are reserved for the future
 * tenant-bound SDK. Platform code injects this interface.</p>
 *
 * <p>Signals: tracing now; metrics will be added to this same interface later
 * (meter(String) — adding an interface method is binary-compatible for callers;
 * only the platform implements this interface).</p>
 *
 * @author onur
 */
public interface Telemetry {

    /** Returns a span-creation helper bound to an instrumentation scope, by convention the
     *  Java package of the instrumenting module, e.g. "com.yahoo.search.dispatch". */
    ScopedTracer tracer(String instrumentationScope);

    /** The configured propagator (W3C trace context); no-op propagator when disabled. */
    TextMapPropagator textMapPropagator();
}
