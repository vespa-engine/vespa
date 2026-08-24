// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.telemetry.api.trace;

import ai.vespa.telemetry.api.NoopTelemetry;
import ai.vespa.telemetry.api.Telemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@link OtelTracing} facade: spans get the default (or explicit) scope, parent under the
 * context they are given, and become non-recording when the carried telemetry is absent.
 *
 * @author onur
 */
class OtelTracingTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider tracerProvider;
    private Telemetry telemetry;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        telemetry = new Telemetry() {
            @Override public ScopedTracer tracer(String scope) { return new ScopedTracer(tracerProvider.get(scope)); }
            @Override public TextMapPropagator textMapPropagator() { return NoopTelemetry.INSTANCE.textMapPropagator(); }
        };
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    void startSpan_uses_the_default_scope() {
        Context parent = Telemetry.store(Context.root(), telemetry);

        OtelTracing.startSpan(parent, "handler X", SpanKind.INTERNAL).end();

        SpanData data = onlySpan();
        assertEquals("handler X", data.getName());
        assertEquals(SpanKind.INTERNAL, data.getKind());
        assertEquals(OtelTracing.DEFAULT_SCOPE, data.getInstrumentationScopeInfo().getName());
    }

    @Test
    void startSpan_honours_an_explicit_scope() {
        Context parent = Telemetry.store(Context.root(), telemetry);

        OtelTracing.startSpan(parent, "custom.scope", "op", SpanKind.INTERNAL).end();

        assertEquals("custom.scope", onlySpan().getInstrumentationScopeInfo().getName());
    }

    @Test
    void startSpan_parents_under_the_span_carried_in_the_context() {
        Span serverParent = telemetry.tracer(OtelTracing.DEFAULT_SCOPE).startSpan("server", SpanKind.SERVER, Context.root());
        Context parent = Telemetry.store(Context.root().with(serverParent), telemetry);

        OtelTracing.startSpan(parent, "handler X", SpanKind.INTERNAL).end();
        serverParent.end();

        assertEquals(serverParent.getSpanContext().getSpanId(), spanNamed("handler X").getParentSpanId());
    }

    @Test
    void startSpan_without_telemetry_is_non_recording_and_exports_nothing() {
        Span span = OtelTracing.startSpan(Context.root(), "handler X", SpanKind.INTERNAL);

        assertFalse(span.isRecording());
        span.end();
        assertTrue(exporter.getFinishedSpanItems().isEmpty(), "no carried telemetry => nothing exported");
    }

    @Test
    void ambient_tracer_reads_telemetry_from_the_current_context() {
        Context ctx = Telemetry.store(Context.root(), telemetry);

        try (Scope ignored = ctx.makeCurrent()) {
            OtelTracing.tracer().startSpan("ambient", SpanKind.INTERNAL, Context.current()).end();
        }

        assertEquals(OtelTracing.DEFAULT_SCOPE, onlySpan().getInstrumentationScopeInfo().getName());
    }

    @Test
    void ambient_tracer_without_a_current_context_is_noop() {
        Span span = OtelTracing.tracer().startSpan("orphan", SpanKind.INTERNAL, Context.current());

        assertFalse(span.isRecording());
        span.end();
        assertTrue(exporter.getFinishedSpanItems().isEmpty());
    }

    @Test
    void inSpan_runs_the_body_in_an_internal_default_scope_span_parented_under_the_carried_span() {
        Span serverParent = telemetry.tracer(OtelTracing.DEFAULT_SCOPE).startSpan("server", SpanKind.SERVER, Context.root());
        Context parent = Telemetry.store(Context.root().with(serverParent), telemetry);
        AtomicReference<Span> current = new AtomicReference<>();

        OtelTracing.instrument(parent, "handler X", () -> current.set(Span.current()));
        serverParent.end();

        SpanData data = spanNamed("handler X");
        assertEquals(SpanKind.INTERNAL, data.getKind());
        assertEquals(OtelTracing.DEFAULT_SCOPE, data.getInstrumentationScopeInfo().getName());
        assertEquals(serverParent.getSpanContext().getSpanId(), data.getParentSpanId());
        assertEquals(data.getSpanId(), current.get().getSpanContext().getSpanId(),
                     "the span must be current during the body");
        assertTrue(data.hasEnded());
    }

    @Test
    void inSpan_without_telemetry_still_runs_the_body_and_exports_nothing() {
        AtomicReference<Boolean> ran = new AtomicReference<>(false);

        OtelTracing.instrument(Context.root(), "handler X", () -> ran.set(true));

        assertTrue(ran.get(), "the body must run even with no telemetry");
        assertTrue(exporter.getFinishedSpanItems().isEmpty(), "no carried telemetry => nothing exported");
    }

    @Test
    void inSpan_supplier_returns_the_body_value_and_ends_a_recording_span() {
        Context parent = Telemetry.store(Context.root(), telemetry);

        String result = OtelTracing.instrument(parent, "chain default", () -> "body-value");

        assertEquals("body-value", result);
        SpanData data = onlySpan();
        assertEquals("chain default", data.getName());
        assertEquals(SpanKind.INTERNAL, data.getKind());
        assertEquals(OtelTracing.DEFAULT_SCOPE, data.getInstrumentationScopeInfo().getName());
        assertTrue(data.hasEnded());
    }

    @Test
    void inSpan_supplier_without_telemetry_still_returns_the_body_value() {
        String result = OtelTracing.instrument(Context.root(), "chain X", () -> "body-value");

        assertEquals("body-value", result);
        assertTrue(exporter.getFinishedSpanItems().isEmpty(), "no carried telemetry => nothing exported");
    }

    @Test
    void ambient_inSpan_reads_the_current_context_and_returns_the_body_value() {
        Context ctx = Telemetry.store(Context.root(), telemetry);

        String result;
        try (Scope ignored = ctx.makeCurrent()) {
            result = OtelTracing.instrument("chain X", () -> "body-value");
        }

        assertEquals("body-value", result);
        SpanData data = onlySpan();
        assertEquals("chain X", data.getName());
        assertEquals(SpanKind.INTERNAL, data.getKind());
        assertEquals(OtelTracing.DEFAULT_SCOPE, data.getInstrumentationScopeInfo().getName());
    }

    @Test
    void inSpan_propagates_a_checked_exception_and_marks_the_span_error() {
        Context parent = Telemetry.store(Context.root(), telemetry);

        IOException thrown = assertThrows(IOException.class,
                                          () -> OtelTracing.instrument(parent, "dispatch.search", () -> { throw new IOException("boom"); }));

        assertEquals("boom", thrown.getMessage());
        SpanData data = onlySpan();
        assertEquals(StatusCode.ERROR, data.getStatus().getStatusCode());
        assertEquals(1, data.getEvents().size(), "the checked exception must be recorded on the span");
        assertTrue(data.hasEnded());
    }

    @Test
    void a_body_throwing_nothing_needs_no_throws_clause_at_the_call_site() {
        // This test is really a COMPILE-time assertion: E infers to RuntimeException, so replacing the
        // Supplier overload with ThrowingSupplier left every existing call site unchanged.
        Context parent = Telemetry.store(Context.root(), telemetry);

        assertEquals("value", OtelTracing.instrument(parent, "chain X", () -> "value"));
    }

    @Test
    void withCurrentContext_restores_the_forking_threads_context_on_the_receiving_thread() throws Exception {
        Context ctx = Telemetry.store(Context.root(), telemetry);
        Span outer = OtelTracing.startSpan(ctx, "outer", SpanKind.INTERNAL);
        AtomicReference<String> seenSpanId = new AtomicReference<>();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            try (Scope ignored = ctx.with(outer).makeCurrent()) {
                executor.submit(OtelTracing.withCurrentContext(() -> seenSpanId.set(Span.current().getSpanContext().getSpanId()))).get();
            }
        } finally {
            shutdown(executor);
        }
        outer.end();

        assertEquals(outer.getSpanContext().getSpanId(), seenSpanId.get(),
                     "the task must run under the span that was current when it was submitted");
    }

    @Test
    void a_task_submitted_directly_sees_no_context_on_the_receiving_thread() throws Exception {
        Context ctx = Telemetry.store(Context.root(), telemetry);
        Span outer = OtelTracing.startSpan(ctx, "outer", SpanKind.INTERNAL);
        AtomicReference<Context> seen = new AtomicReference<>();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            try (Scope ignored = ctx.with(outer).makeCurrent()) {
                executor.submit(() -> seen.set(Context.current())).get();
            }
        } finally {
            shutdown(executor);
        }
        outer.end();

        // The control for the test above: the OpenTelemetry context is a plain, non-inheritable ThreadLocal,
        // so without withCurrentContext() nothing crosses the thread boundary. This is the condition L4 exists to fix.
        assertFalse(Span.fromContext(seen.get()).getSpanContext().isValid(),
                    "a task submitted directly must see no span, otherwise this test proves nothing about withCurrentContext()");
    }

    @Test
    void a_span_created_inside_a_context_propagating_task_parents_to_the_forking_span() throws Exception {
        Context ctx = Telemetry.store(Context.root(), telemetry);
        Span outer = OtelTracing.startSpan(ctx, "outer", SpanKind.INTERNAL);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            try (Scope ignored = ctx.with(outer).makeCurrent()) {
                executor.submit(OtelTracing.withCurrentContext(() -> OtelTracing.instrument("forked", () -> { }))).get();
            }
        } finally {
            shutdown(executor);
        }
        outer.end();

        SpanData forked = spanNamed("forked");
        assertEquals(outer.getSpanContext().getSpanId(), forked.getParentSpanId(),
                     "a span created on the receiving thread must be a child of the forking span");
        assertEquals(outer.getSpanContext().getTraceId(), forked.getTraceId(), "and stay in the same trace");
    }

    @Test
    void a_context_propagating_task_does_not_leak_context_into_the_next_task_on_that_thread() throws Exception {
        Context ctx = Telemetry.store(Context.root(), telemetry);
        Span outer = OtelTracing.startSpan(ctx, "outer", SpanKind.INTERNAL);
        AtomicReference<Context> afterwards = new AtomicReference<>();

        ExecutorService executor = Executors.newSingleThreadExecutor();   // single thread: the same one runs both
        try {
            try (Scope ignored = ctx.with(outer).makeCurrent()) {
                executor.submit(OtelTracing.withCurrentContext(() -> { })).get();
            }
            executor.submit(() -> afterwards.set(Context.current())).get();
        } finally {
            shutdown(executor);
        }
        outer.end();

        assertFalse(Span.fromContext(afterwards.get()).getSpanContext().isValid(),
                    "withCurrentContext() must close its scope, leaving the pooled thread clean for an unrelated later task");
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "executor did not terminate");
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
