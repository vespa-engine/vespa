// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.telemetry.api;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author onur
 */
class NoopTelemetryTest {

    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        @Override public Iterable<String> keys(Map<String, String> carrier) { return carrier.keySet(); }
        @Override public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    @Test
    void spans_are_non_recording() {
        Span span = NoopTelemetry.INSTANCE.tracer("scope").startSpan("x", SpanKind.SERVER, Context.root());

        assertFalse(span.isRecording());
        assertFalse(span.getSpanContext().isValid());
        span.end();
    }

    @Test
    void inSpan_still_runs_the_body_and_returns_its_value() {
        String result = NoopTelemetry.INSTANCE.tracer("scope")
                .inSpan("x", SpanKind.INTERNAL, Context.root(), () -> "body-value");

        assertEquals("body-value", result);
    }

    @Test
    void inSpan_still_propagates_exceptions() {
        IllegalStateException boom = new IllegalStateException("boom");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> NoopTelemetry.INSTANCE.tracer("scope")
                        .inSpan("x", SpanKind.INTERNAL, Context.root(), () -> { throw boom; }));

        assertSame(boom, thrown);
    }

    @Test
    void textMapPropagator_extraction_is_inert() {
        TextMapPropagator propagator = NoopTelemetry.INSTANCE.textMapPropagator();
        assertTrue(propagator.fields().isEmpty());

        Map<String, String> carrier = Map.of("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
        Context extracted = propagator.extract(Context.root(), carrier, GETTER);

        assertSame(Context.root(), extracted, "the no-op propagator must return the context unchanged");
        assertFalse(Span.fromContext(extracted).getSpanContext().isValid(), "no remote parent must be extracted");
    }

    @Test
    void tracer_is_usable_for_any_scope() {
        assertNotNull(NoopTelemetry.INSTANCE.tracer("com.yahoo.search.dispatch"));
    }
}
