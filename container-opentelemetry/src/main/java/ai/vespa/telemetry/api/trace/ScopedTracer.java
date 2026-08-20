// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.telemetry.api.trace;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Span-creation helpers over one instrumentation scope.
 *
 * <p>INVARIANT (enforced in review): every signature uses OpenTelemetry types. This class may
 * compose, apply defaults and policy, and remove boilerplate; it must never declare a type that
 * re-represents an OpenTelemetry concept.</p>
 *
 * @author onur
 */
public final class ScopedTracer {

    private static final Logger log = Logger.getLogger(ScopedTracer.class.getName());

    private final Tracer tracer;

    public ScopedTracer(Tracer tracer) { this.tracer = tracer; }

    /** Instruments a body that RETURNS a value: runs it inside a span on the current thread - start, make
     *  current, record failure, end - and passes the value back through. Takes a {@link ThrowingSupplier} so a
     *  body declaring {@code throws} can be instrumented without altering its exception behaviour; a
     *  non-throwing body infers {@code E = RuntimeException}. */
    public <T, E extends Exception> T instrument(String name, SpanKind kind, Context parent, ThrowingSupplier<T, E> body) throws E {
        Span span = startSpan(name, kind, parent);
        try (Scope ignored = span.makeCurrent()) {
            return body.get();
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR);
            throw t;
        } finally {
            span.end();
        }
    }

    /** As above, for a body that produces no value. */
    public void instrument(String name, SpanKind kind, Context parent, Runnable body) {
        instrument(name, kind, parent, () -> { body.run(); return null; });
    }

    /** For spans ended on a DIFFERENT thread (RPC replies etc.): caller owns end(); Span is thread-safe.
     *  Never throws: on any failure to create the span (e.g. a sampler or SpanProcessor.onStart that throws),
     *  logs and returns a non-recording invalid span, so instrumentation can never break the caller. This is
     *  the single creation choke point — {@link #instrument} and the {@link OtelTracing} facade both route
     *  through it. */
    public Span startSpan(String name, SpanKind kind, Context parent) {
        try {
            return tracer.spanBuilder(name).setSpanKind(kind).setParent(parent).startSpan();
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to start span '" + name + "'", e);
            return Span.getInvalid();
        }
    }

    /** Convenience for the common server-side case: a {@link SpanKind#SERVER} span under {@code parent}.
     *  Ended by the caller (possibly on another thread) — same contract as {@link #startSpan}. */
    public Span startServerSpan(String name, Context parent) { return startSpan(name, SpanKind.SERVER, parent); }

    /** The raw {@link SpanBuilder} for this scope: full control before starting (attributes at creation,
     *  links, multiple parents, explicit start timestamp). Prefer {@link #startSpan}/{@link #instrument} for the
     *  common cases; use this when they are not expressive enough. */
    public SpanBuilder spanBuilder(String spanName) { return tracer.spanBuilder(spanName); }

    /** Escape hatch: the full OpenTelemetry API (links, events, attributes-at-creation). */
    public Tracer otelTracer() { return tracer; }
}
