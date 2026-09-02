// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.telemetry.api;

import ai.vespa.telemetry.api.trace.ScopedTracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.propagation.TextMapPropagator;

import java.util.Objects;

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
 * <p>{@link #store}/{@link #from} carry this instance inside an OpenTelemetry {@link Context}, for
 * instrumentation sites that cannot be dependency-injected — notably searchers and executions.</p>
 *
 * @author onur
 */
public interface Telemetry {

    /** Returns a span-creation helper bound to an instrumentation scope, by convention the
     *  Java package of the instrumenting module, e.g. "com.yahoo.search.dispatch". */
    ScopedTracer tracer(String instrumentationScope);

    /** The configured propagator (W3C trace context); no-op propagator when disabled. */
    TextMapPropagator textMapPropagator();

    /**
     * Returns a context carrying {@code telemetry}, or {@code ctx} unchanged when there is nothing to carry.
     * A null or no-op telemetry is tolerated on purpose: telemetry may legitimately be unconfigured, whereas a
     * missing context is a programming error.
     *
     * @throws NullPointerException if {@code ctx} is null
     */
    static Context store(Context ctx, Telemetry telemetry) {
        Objects.requireNonNull(ctx, "ctx");
        return (telemetry == null || telemetry == NoopTelemetry.INSTANCE) ? ctx : ctx.with(Key.INSTANCE, telemetry);
    }

    /**
     * The telemetry carried in {@code ctx}, or the no-op instance when absent. Never null.
     *
     * @throws NullPointerException if {@code ctx} is null
     */
    static Telemetry from(Context ctx) {
        Objects.requireNonNull(ctx, "ctx");
        Telemetry telemetry = ctx.get(Key.INSTANCE);
        return telemetry != null ? telemetry : NoopTelemetry.INSTANCE;
    }

    /**
     * Holds the carrier key privately: a bare interface field would be {@code public static final}, exposing it.
     * {@link ContextKey} is compared by reference, so this must remain a single shared instance; the enclosing
     * interface reaches it as a nestmate.
     */
    final class Key {
        private static final ContextKey<Telemetry> INSTANCE = ContextKey.named("ai.vespa.telemetry");
        private Key() { }
    }
}
