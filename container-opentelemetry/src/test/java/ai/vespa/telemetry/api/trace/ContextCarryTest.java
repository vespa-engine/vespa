// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.telemetry.api.trace;

import ai.vespa.telemetry.api.NoopTelemetry;
import ai.vespa.telemetry.api.Telemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the PRODUCTION shape, which no existing test covers: a span started from an EXPLICIT parent
 * context (as ThreadedRequestHandler does, reading it off the request) and then, from inside that body,
 * a nested AMBIENT call that has to find the telemetry through Context.current().
 *
 * <p>Every other test makes the whole context current first, so the ambient lookup always succeeds there.</p>
 *
 * @author onur
 */
class ContextCarryTest {

    @Test
    void telemetry_survives_into_a_nested_ambient_span() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        Telemetry telemetry = new Telemetry() {
            @Override public ScopedTracer tracer(String scope) { return new ScopedTracer(provider.get(scope)); }
            @Override public TextMapPropagator textMapPropagator() { return NoopTelemetry.INSTANCE.textMapPropagator(); }
        };

        // What JettyServerSpanHandler builds and stores on the request: telemetry carried in a context that
        // is NOT current on the worker thread.
        Context requestContext = Telemetry.store(Context.root(), telemetry);

        // What ThreadedRequestHandler does: explicit parent, then everything below it is ambient.
        OtelTracing.instrument(requestContext, "handler.Test", () ->
                OtelTracing.instrument("chain.search", () -> { }));

        var spans = List.copyOf(exporter.getFinishedSpanItems());   // snapshot BEFORE close: shutdown clears it
        provider.close();
        assertEquals(2, spans.size(),
                     "both the handler span and the nested ambient span must be recorded; if only one is, the " +
                     "telemetry did not survive into Context.current() and everything below the handler is lost");

        // Recorded is not enough - the nested span must also attach to the handler span, which only holds if
        // the SPAN travelled in the context too, not just the telemetry.
        var handler = spans.stream().filter(s2 -> s2.getName().equals("handler.Test")).findFirst().orElseThrow();
        var nested  = spans.stream().filter(s2 -> s2.getName().equals("chain.search")).findFirst().orElseThrow();
        assertEquals(handler.getSpanId(), nested.getParentSpanId(), "chain.search must be a child of handler.Test");
        assertEquals(handler.getTraceId(), nested.getTraceId(), "and stay in the same trace");
    }

    @Test
    void the_nested_span_is_a_child_of_the_outer_one() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        Telemetry telemetry = new Telemetry() {
            @Override public ScopedTracer tracer(String scope) { return new ScopedTracer(provider.get(scope)); }
            @Override public TextMapPropagator textMapPropagator() { return NoopTelemetry.INSTANCE.textMapPropagator(); }
        };
        Context requestContext = Telemetry.store(Context.root(), telemetry);

        OtelTracing.instrument(requestContext, "handler.Test", () ->
                OtelTracing.startSpan(Context.current(), "node.search", SpanKind.CLIENT).end());

        var spans = List.copyOf(exporter.getFinishedSpanItems());   // snapshot BEFORE close
        provider.close();
        assertEquals(2, spans.size(), "the CLIENT span started from the ambient context must be recorded too");
        var handler = spans.stream().filter(s2 -> s2.getName().equals("handler.Test")).findFirst().orElseThrow();
        var node    = spans.stream().filter(s2 -> s2.getName().equals("node.search")).findFirst().orElseThrow();
        assertEquals(handler.getSpanId(), node.getParentSpanId(), "node.search must be a child of handler.Test");
    }

    @Test
    void telemetry_survives_an_explicit_parent_followed_by_a_thread_fork() throws Exception {
        // The L4 production shape: explicit parent at the handler, then work handed to a pool thread.
        // withCurrentContext captures Context.current(), so if the telemetry never reached the thread-local
        // the forked task creates non-recording spans and the whole sub-chain vanishes from the trace.
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        Telemetry telemetry = new Telemetry() {
            @Override public ScopedTracer tracer(String scope) { return new ScopedTracer(provider.get(scope)); }
            @Override public TextMapPropagator textMapPropagator() { return NoopTelemetry.INSTANCE.textMapPropagator(); }
        };
        Context requestContext = Telemetry.store(Context.root(), telemetry);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        OtelTracing.instrument(requestContext, "handler.Test", () -> {
            executor.execute(OtelTracing.withCurrentContext(() ->
                    OtelTracing.instrument("dispatch.search", () -> { })));
            executor.shutdown();
            try { assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "fork did not finish"); }
            catch (InterruptedException e) { throw new RuntimeException(e); }
        });

        var spans = List.copyOf(exporter.getFinishedSpanItems());
        provider.close();

        assertEquals(2, spans.size(), "the span created on the pool thread must be recorded");
        var handler = spans.stream().filter(s -> s.getName().equals("handler.Test")).findFirst().orElseThrow();
        var forked  = spans.stream().filter(s -> s.getName().equals("dispatch.search")).findFirst().orElseThrow();
        assertEquals(handler.getSpanId(), forked.getParentSpanId(), "and be a child of the forking span");
        assertEquals(handler.getTraceId(), forked.getTraceId(), "and stay in the same trace");
    }
}
