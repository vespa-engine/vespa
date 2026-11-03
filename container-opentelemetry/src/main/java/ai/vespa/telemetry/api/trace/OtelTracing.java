// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.telemetry.api.trace;

import ai.vespa.telemetry.api.Telemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;


/**
 * OpenTelemetry tracing facade over the {@link Telemetry} carried in an OpenTelemetry {@link Context}
 * (see {@link Telemetry#store}/{@link Telemetry#from}). Span sites call this instead of unpacking
 * telemetry &rarr; tracer &rarr; span by hand. Non-recording — and nothing exported — when the carried
 * telemetry is absent or disabled, so sites instrument unconditionally.
 *
 * <p>Named for OpenTelemetry deliberately: Vespa already has an unrelated {@code Trace} — the per-query
 * diagnostic trace reachable through {@code Query.trace(..)} — and call sites here sit within a few lines of
 * it. Nothing in this class has anything to do with that.</p>
 *
 * @author onur
 */
public final class OtelTracing {

    /** Single instrumentation scope for Vespa-internal spans; pass an explicit scope only when you truly need one. */
    public static final String DEFAULT_SCOPE = "ai.vespa";

    private OtelTracing() { }

    /**
     * Starts a span whose parent span AND {@link Telemetry} are both taken from {@code parent}, under the
     * {@link #DEFAULT_SCOPE}. For boundary layers where the context is not yet current (e.g. a handler entry);
     * the caller owns {@code makeCurrent()} / {@code end()}.
     */
    public static Span startSpan(Context parent, String name, SpanKind kind) {
        return startSpan(parent, DEFAULT_SCOPE, name, kind);
    }

    /** As {@link #startSpan(Context, String, SpanKind)} but under an explicit instrumentation scope. */
    public static Span startSpan(Context parent, String scope, String name, SpanKind kind) {
        return Telemetry.from(parent).tracer(scope).startSpan(name, kind, parent);
    }

    /**
     * Instruments {@code body}: RUNS it inside an {@link SpanKind#INTERNAL INTERNAL} span under the
     * {@link #DEFAULT_SCOPE}, whose parent span AND {@link Telemetry} are both taken from {@code parent}. Starts
     * the span, makes it current for the duration of {@code body}, records the exception and sets ERROR status if
     * {@code body} throws (then rethrows), and always ends the span. Best-effort — non-recording, and nothing
     * exported, when the carried telemetry is absent or disabled, so callers instrument unconditionally.
     */
    public static void instrument(Context parent, String name, Runnable body) {
        Telemetry.from(parent).tracer(DEFAULT_SCOPE).instrument(name, SpanKind.INTERNAL, parent, body);
    }

    /** Ambient form of {@link #instrument(Context, String, Runnable)}: the parent span and {@link Telemetry} are
     *  taken from {@link Context#current()}. Use downstream of the handler entry, where the context is already
     *  current; lets call sites instrument without naming {@link Context} (some Vespa classes shadow that name). */
    public static void instrument(String name, Runnable body) {
        instrument(Context.current(), name, body);
    }

    /**
     * As {@link #instrument(Context, String, Runnable)}, for a body that RETURNS a value, which is passed back
     * through. Takes a {@link ThrowingSupplier}, so a body declaring {@code throws} can be instrumented; a body
     * that throws no checked exception infers {@code E = RuntimeException} and the call site needs no
     * {@code throws}.
     */
    public static <T, E extends Exception> T instrument(Context parent, String name, ThrowingSupplier<T, E> body) throws E {
        return Telemetry.from(parent).tracer(DEFAULT_SCOPE).instrument(name, SpanKind.INTERNAL, parent, body);
    }

    /** Ambient form of {@link #instrument(Context, String, ThrowingSupplier)}. */
    public static <T, E extends Exception> T instrument(String name, ThrowingSupplier<T, E> body) throws E {
        return instrument(Context.current(), name, body);
    }

    /**
     * Returns {@code task} bound to the CURRENT context, so the thread that later runs it does so under the same
     * span and {@link Telemetry} as the thread that submitted it. Use at a fork — a point where request work is
     * handed to another thread — since the OpenTelemetry context lives in a plain, non-inheritable ThreadLocal and
     * would otherwise be absent there, leaving spans on the receiving thread non-recording and unexported.
     *
     * <p>The context is captured HERE, at call time on the forking thread, not when the task later runs. The scope
     * is closed when the task returns, so a pooled thread never leaks context into an unrelated later task.</p>
     *
     * <p>Unlike {@link #instrument}, this RUNS NOTHING — it returns a wrapper for the caller to submit.</p>
     *
     * <p>This is propagation ACROSS THREADS within one process. Propagation across processes is a different
     * mechanism — {@link Telemetry#textMapPropagator()} serialises the context into carrier headers.</p>
     */
    public static Runnable withCurrentContext(Runnable task) {
        return Context.current().wrap(task);
    }

    /**
     * A tracer under the {@link #DEFAULT_SCOPE}, backed by the telemetry in the CURRENT context. Use only where
     * the context is already current (downstream of the handler entry); returns a no-op tracer otherwise.
     */
    public static ScopedTracer tracer() {
        return Telemetry.from(Context.current()).tracer(DEFAULT_SCOPE);
    }

    public static ScopedTracer tracer(Context context) {
        return Telemetry.from(context).tracer(DEFAULT_SCOPE);
    }

    /** As {@link #tracer()} but under an explicit instrumentation scope. */
    public static ScopedTracer tracer(String scope) {
        return Telemetry.from(Context.current()).tracer(scope);
    }
}
