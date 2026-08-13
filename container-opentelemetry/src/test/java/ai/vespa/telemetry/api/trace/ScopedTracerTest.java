// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.telemetry.api.trace;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author onur
 */
class ScopedTracerTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider tracerProvider;
    private ScopedTracer tracer;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        tracer = new ScopedTracer(tracerProvider.get("ai.vespa.telemetry.api.trace.test"));
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    void inSpan_returns_body_value_and_ends_span_without_error_status() {
        String result = tracer.inSpan("success", SpanKind.INTERNAL, Context.root(), () -> "body-value");

        assertEquals("body-value", result);
        SpanData span = onlySpan();
        assertEquals("success", span.getName());
        assertEquals(SpanKind.INTERNAL, span.getKind());
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode());
        assertTrue(span.hasEnded());
        assertTrue(span.getEvents().isEmpty());
    }

    @Test
    void inSpan_makes_the_span_current_for_the_body() {
        AtomicReference<Span> seen = new AtomicReference<>();
        tracer.inSpan("current", SpanKind.INTERNAL, Context.root(), () -> seen.set(Span.current()));

        assertEquals(onlySpan().getSpanId(), seen.get().getSpanContext().getSpanId());
    }

    @Test
    void inSpan_records_exception_sets_error_status_ends_span_and_propagates() {
        IllegalStateException boom = new IllegalStateException("boom");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> tracer.inSpan("failing", SpanKind.INTERNAL, Context.root(), () -> { throw boom; }));

        assertSame(boom, thrown);
        SpanData span = onlySpan();
        assertEquals("failing", span.getName());
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        assertTrue(span.hasEnded());
        assertEquals(1, span.getEvents().size(), "expected the recorded exception event");
        assertEquals("exception", span.getEvents().get(0).getName());
    }

    @Test
    void inSpan_runnable_overload_runs_body_and_ends_span() {
        AtomicReference<String> ran = new AtomicReference<>();
        tracer.inSpan("runnable", SpanKind.INTERNAL, Context.root(), (Runnable) () -> ran.set("ran"));

        assertEquals("ran", ran.get());
        SpanData span = onlySpan();
        assertEquals("runnable", span.getName());
        assertTrue(span.hasEnded());
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode());
    }

    @Test
    void startSpan_can_be_ended_from_a_different_thread_and_keeps_its_parent() throws InterruptedException {
        Span parent = tracer.startSpan("parent", SpanKind.INTERNAL, Context.root());
        Span child = tracer.startSpan("child", SpanKind.SERVER, Context.root().with(parent));

        Thread ender = new Thread(child::end, "span-ender");
        ender.start();
        ender.join();
        parent.end();

        SpanData childData = spanNamed("child");
        assertEquals(SpanKind.SERVER, childData.getKind());
        assertTrue(childData.hasEnded());
        assertEquals(parent.getSpanContext().getSpanId(), childData.getParentSpanId());
        assertEquals(parent.getSpanContext().getTraceId(), childData.getTraceId());
    }

    @Test
    void startSpan_does_not_make_the_span_current() {
        Span span = tracer.startSpan("not-current", SpanKind.INTERNAL, Context.root());
        assertFalse(Span.current().getSpanContext().isValid(), "startSpan must not touch the current context");
        span.end();
    }

    @Test
    void startServerSpan_creates_a_server_kind_span_under_the_given_parent() {
        Span parent = tracer.startSpan("parent", SpanKind.INTERNAL, Context.root());
        Span child = tracer.startServerSpan("child", Context.root().with(parent));
        child.end();
        parent.end();

        SpanData childData = spanNamed("child");
        assertEquals(SpanKind.SERVER, childData.getKind());
        assertEquals(parent.getSpanContext().getSpanId(), childData.getParentSpanId());
    }

    @Test
    void spanBuilder_exposes_the_raw_builder_for_attributes_at_creation() {
        Span span = tracer.spanBuilder("built")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("k", "v")
                .startSpan();
        span.end();

        SpanData data = onlySpan();
        assertEquals("built", data.getName());
        assertEquals(SpanKind.CLIENT, data.getKind());
        assertEquals("v", data.getAttributes().get(AttributeKey.stringKey("k")));
    }

    @Test
    void otelTracer_returns_the_underlying_tracer() {
        assertSame(tracer.otelTracer(), tracer.otelTracer());
    }

    private SpanData onlySpan() {
        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertEquals(1, spans.size(), "expected exactly one span, got " + spans);
        return spans.get(0);
    }

    private SpanData spanNamed(String name) {
        return exporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no span named " + name));
    }
}
