// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.test;

import ai.vespa.telemetry.api.NoopTelemetry;
import ai.vespa.telemetry.api.Telemetry;
import ai.vespa.telemetry.api.trace.ScopedTracer;
import ai.vespa.telemetry.api.trace.OtelTracing;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test fixture for the internal OpenTelemetry span instrumentation. It installs a RECORDING {@link Telemetry}
 * into an OpenTelemetry {@link Context} beneath a parent span, makes that context current while the code under
 * test runs — exactly as L2 does on the worker thread and L4 does on a fork thread — and collects whatever the
 * instrumentation emitted.
 *
 * <p>Using this rather than hand-rolling the setup also removes a silent-failure mode: with no telemetry in the
 * current context, {@link Telemetry#from} yields the no-op instance and the code under test emits nothing, so a
 * test that forgot the fixture would PASS while asserting on an empty exporter.</p>
 *
 * <p>Typical use:</p>
 * <pre>
 * SpanRecorder recorder = SpanRecorder.underParentSpan("chain");
 * recorder.record(() -&gt; execution.search(new Query("?query=test")));
 *
 * SpanData searcherSpan = recorder.spanNamed("searcher A");
 * assertEquals(recorder.parentSpan().getSpanContext().getSpanId(), searcherSpan.getParentSpanId());
 * recorder.close();
 * </pre>
 *
 * @author onur
 */
public final class SpanRecorder implements AutoCloseable {

    /** A {@link Runnable} that may throw a checked exception, so code under test declaring {@code throws} fits. */
    @FunctionalInterface
    public interface ThrowingRunnable<E extends Exception> {
        void run() throws E;
    }

    private final InMemorySpanExporter exporter;
    private final SdkTracerProvider tracerProvider;
    private final Telemetry telemetry;
    private final Span parent;
    private final Context context;

    /** Snapshot taken before the provider is shut down; see {@link #spans()}. */
    private List<SpanData> collected;

    private SpanRecorder(String parentSpanName) {
        exporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        telemetry = new Telemetry() {
            @Override public ScopedTracer tracer(String scope) { return new ScopedTracer(tracerProvider.get(scope)); }
            @Override public TextMapPropagator textMapPropagator() { return NoopTelemetry.INSTANCE.textMapPropagator(); }
        };
        parent = telemetry.tracer(OtelTracing.DEFAULT_SCOPE).startSpan(parentSpanName, SpanKind.INTERNAL, Context.root());
        context = Telemetry.store(Context.root().with(parent), telemetry);
    }

    /** A recorder whose emitted spans will hang beneath a recording parent span with the given name. */
    public static SpanRecorder underParentSpan(String parentSpanName) {
        return new SpanRecorder(parentSpanName);
    }

    /** The parent span; assert against {@code parentSpan().getSpanContext().getSpanId()} to check parenting. */
    public Span parentSpan() { return parent; }

    /** The context carrying the parent span and the recording telemetry, for tests that must make it current themselves. */
    public Context context() { return context; }

    /** The recording telemetry, for tests that need to create spans of their own. */
    public Telemetry telemetry() { return telemetry; }

    /** Runs {@code body} with the recording context current. */
    public <E extends Exception> void record(ThrowingRunnable<E> body) throws E {
        // Explicit Scope + finally rather than try-with-resources: container-search compiles tests with
        // -Werror -Xlint:try, which rejects an unreferenced auto-closeable resource.
        Scope scope = context.makeCurrent();
        try {
            body.run();
        } finally {
            scope.close();
        }
    }

    /**
     * Every span emitted, including the parent, which is ended on the first call.
     *
     * <p>The result is snapshotted, because {@code tracerProvider.close()} shuts the exporter down and
     * {@link InMemorySpanExporter#shutdown} CLEARS what it collected. Snapshotting here makes the
     * assert-after-close mistake impossible.</p>
     */
    public List<SpanData> spans() {
        if (collected == null) {
            parent.end();
            collected = List.copyOf(exporter.getFinishedSpanItems());
        }
        return collected;
    }

    /** The single span with this exact name; fails the test if there is not exactly one. */
    public SpanData spanNamed(String name) {
        List<SpanData> matching = spans().stream().filter(s -> s.getName().equals(name)).toList();
        assertEquals(1, matching.size(), "expected exactly one span named '" + name + "', got " + spans());
        return matching.get(0);
    }

    /** How many spans have a name starting with {@code prefix} — for asserting that none were emitted. */
    public long countSpans(String prefix) {
        return spans().stream().filter(s -> s.getName().startsWith(prefix)).count();
    }

    /** Idempotent; safe to call after asserting, and safe not to call at all. */
    @Override
    public void close() {
        spans();                  // snapshot before the exporter is cleared
        tracerProvider.close();
    }
}
