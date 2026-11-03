// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.telemetry.api;

import ai.vespa.telemetry.api.trace.ScopedTracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises {@link Telemetry#store}/{@link Telemetry#from} — carrying the telemetry through an OpenTelemetry
 * {@link Context}.
 *
 * @author onur
 */
class TelemetryTest {

    /** A Telemetry that is not the no-op, so that store() does not short-circuit. */
    private static final Telemetry TELEMETRY = new Telemetry() {
        @Override public ScopedTracer tracer(String scope) { return NoopTelemetry.INSTANCE.tracer(scope); }
        @Override public TextMapPropagator textMapPropagator() { return NoopTelemetry.INSTANCE.textMapPropagator(); }
    };

    @Test
    void stored_telemetry_is_returned() {
        Context context = Telemetry.store(Context.root(), TELEMETRY);

        assertSame(TELEMETRY, Telemetry.from(context));
    }

    @Test
    void an_empty_context_yields_the_noop_rather_than_null() {
        Telemetry telemetry = Telemetry.from(Context.root());

        assertNotNull(telemetry);
        assertSame(NoopTelemetry.INSTANCE, telemetry);
    }

    @Test
    void storing_the_noop_leaves_the_context_untouched() {
        Context root = Context.root();

        assertSame(root, Telemetry.store(root, NoopTelemetry.INSTANCE));
    }

    @Test
    void storing_null_leaves_the_context_untouched() {
        Context root = Context.root();

        assertSame(root, Telemetry.store(root, null));
    }

    @Test
    void store_does_not_mutate_the_context_it_was_given() {
        Context root = Context.root();

        Telemetry.store(root, TELEMETRY);

        assertSame(NoopTelemetry.INSTANCE, Telemetry.from(root),
                   "Context is immutable: store() must return a new context, never modify the original");
    }

    /**
     * The load-bearing property: L1 hands us a context that already carries the SERVER span and the
     * parent extracted from the incoming W3C headers. Storing telemetry must ADD to that context, never
     * rebuild it — otherwise every trace silently loses its parent.
     */
    @Test
    void store_preserves_everything_already_in_the_context() {
        Span span = Span.wrap(SpanContext.create("0af7651916cd43dd8448eb211c80319c",
                                                 "b7ad6b7169203331",
                                                 TraceFlags.getSampled(),
                                                 TraceState.getDefault()));
        Context withSpan = Context.root().with(span);

        Context withBoth = Telemetry.store(withSpan, TELEMETRY);

        assertSame(TELEMETRY, Telemetry.from(withBoth));
        assertSame(span, Span.fromContext(withBoth),
                   "storing telemetry must not drop what the context already carried");
    }

    @Test
    void from_rejects_a_null_context() {
        assertThrows(NullPointerException.class, () -> Telemetry.from(null));
    }

    @Test
    void store_rejects_a_null_context() {
        assertThrows(NullPointerException.class, () -> Telemetry.store(null, TELEMETRY));
    }

    @Test
    void telemetry_is_reachable_through_the_current_context() {
        Context context = Telemetry.store(Context.root(), TELEMETRY);

        try (Scope ignored = context.makeCurrent()) {
            assertSame(TELEMETRY, Telemetry.from(Context.current()));
        }

        assertSame(NoopTelemetry.INSTANCE, Telemetry.from(Context.current()),
                   "closing the scope must restore a context without telemetry");
    }
}
