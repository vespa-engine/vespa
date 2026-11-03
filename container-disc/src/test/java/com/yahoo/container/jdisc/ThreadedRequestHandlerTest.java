// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import ai.vespa.telemetry.api.NoopTelemetry;
import ai.vespa.telemetry.api.Telemetry;
import ai.vespa.telemetry.api.trace.ScopedTracer;
import ai.vespa.telemetry.api.trace.OtelTracing;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.BufferedContentChannel;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.server.jetty.RequestUtils;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the per-handler INTERNAL span that {@link ThreadedRequestHandler#processRequest} creates: it is a
 * child of the SERVER context bridged into the jdisc request, is made current for the handler body, and is
 * non-recording when no telemetry rode along.
 *
 * @author onur
 */
class ThreadedRequestHandlerTest {

    @Test
    void creates_a_child_handler_span_made_current_for_the_handler_body() throws InterruptedException {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        Telemetry telemetry = recording(tracerProvider);
        Span serverSpan = telemetry.tracer(OtelTracing.DEFAULT_SCOPE).startSpan("server", SpanKind.SERVER, Context.root());
        Context bridged = Telemetry.store(Context.root().with(serverSpan), telemetry);

        // A real worker thread, not Runnable::run: with a same-thread executor, processRequest runs inside
        // request.connect() before the driver writes/closes the request body, so consumeRequestContent()
        // deadlocks draining a body that never arrives. A real executor lets connect() return first.
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CapturingHandler handler = new CapturingHandler(executor);
        try (RequestHandlerTestDriver driver = new RequestHandlerTestDriver(handler)) {
            Request request = driver.createRequest("http://localhost/foo", HttpRequest.Method.GET);
            request.context().put(RequestUtils.JDISC_REQUEST_OTEL_CONTEXT, bridged);
            driver.sendRequest(request, "");
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "the handler task must complete");
        }
        serverSpan.end();

        SpanData handlerSpan = spanNamed(exporter, "handler.CapturingHandler");
        assertEquals(SpanKind.INTERNAL, handlerSpan.getKind());
        assertEquals(OtelTracing.DEFAULT_SCOPE, handlerSpan.getInstrumentationScopeInfo().getName());
        assertEquals(serverSpan.getSpanContext().getSpanId(), handlerSpan.getParentSpanId(),
                     "the handler span must be a child of the bridged SERVER span");
        assertEquals(handlerSpan.getSpanId(), handler.current.get().getSpanContext().getSpanId(),
                     "the handler span must be current during handleRequest");

        tracerProvider.close();
    }

    @Test
    void handler_span_is_non_recording_when_no_telemetry_is_carried() throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();   // real worker (see the child-span test)
        CapturingHandler handler = new CapturingHandler(executor);
        try (RequestHandlerTestDriver driver = new RequestHandlerTestDriver(handler)) {
            driver.sendRequest("http://localhost/foo");   // request carries no OTel context
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "the handler task must complete");
        }

        Span current = handler.current.get();
        assertNotNull(current, "handleRequest must still run");
        assertFalse(current.getSpanContext().isValid(), "no carried telemetry => invalid, non-recording span");
        assertFalse(current.isRecording());
    }

    @Test
    void handler_that_throws_records_the_exception_and_still_responds_500() throws InterruptedException {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        Telemetry telemetry = recording(tracerProvider);
        Span serverSpan = telemetry.tracer(OtelTracing.DEFAULT_SCOPE).startSpan("server", SpanKind.SERVER, Context.root());
        Context bridged = Telemetry.store(Context.root().with(serverSpan), telemetry);

        // A real worker thread (not Runnable::run): with a same-thread executor, processRequest would run
        // inside request.connect() before the driver writes/closes the request body, so consumeRequestContent()
        // would deadlock draining a body that never arrives. A real executor lets connect() return first.
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ThrowingHandler handler = new ThrowingHandler(executor);
        try (RequestHandlerTestDriver driver = new RequestHandlerTestDriver(handler)) {
            Request request = driver.createRequest("http://localhost/foo", HttpRequest.Method.GET);
            request.context().put(RequestUtils.JDISC_REQUEST_OTEL_CONTEXT, bridged);
            var response = driver.sendRequest(request, "").awaitResponse();   // wait for the auto-generated 500
            assertEquals(500, response.getStatus(), "a throwing handler must still produce a 500 response");
            response.readAll();
        }
        executor.shutdown();
        serverSpan.end();

        SpanData handlerSpan = spanNamed(exporter, "handler.ThrowingHandler");
        assertEquals(StatusCode.ERROR, handlerSpan.getStatus().getStatusCode());
        assertTrue(handlerSpan.hasEnded(), "the handler span must be ended even when the handler throws");
        assertEquals(1, handlerSpan.getEvents().size(), "the thrown exception must be recorded on the span");
        assertEquals("exception", handlerSpan.getEvents().get(0).getName());

        tracerProvider.close();
    }

    @Test
    void async_handler_span_ends_when_handleRequest_returns_before_the_async_response() throws InterruptedException {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        Telemetry telemetry = recording(tracerProvider);
        Span serverSpan = telemetry.tracer(OtelTracing.DEFAULT_SCOPE).startSpan("server", SpanKind.SERVER, Context.root());
        Context bridged = Telemetry.store(Context.root().with(serverSpan), telemetry);

        ExecutorService executor = Executors.newSingleThreadExecutor();   // real worker (see the throwing-handler test)
        AsyncHandler handler = new AsyncHandler(executor);
        try (RequestHandlerTestDriver driver = new RequestHandlerTestDriver(handler)) {
            Request request = driver.createRequest("http://localhost/foo", HttpRequest.Method.GET);
            request.context().put(RequestUtils.JDISC_REQUEST_OTEL_CONTEXT, bridged);
            var response = driver.sendRequest(request, "");
            executor.shutdown();
            boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);   // handleRequest returned; span ended

            // Capture the outcome, then dispatch the deferred response BEFORE asserting, so a failing
            // assertion can never leave the driver without a response (which would block close()).
            Response synchronousResponse = response.getResponse();
            Span currentDuringHandle = handler.current.get();
            handler.respond();
            response.readAll();

            assertTrue(finished, "the handler task must complete");
            assertNull(synchronousResponse, "an async handler must not respond synchronously");
            SpanData handlerSpan = spanNamed(exporter, "handler.AsyncHandler");
            assertTrue(handlerSpan.hasEnded(), "the span must end when handleRequest returns, before the async response");
            assertEquals(StatusCode.UNSET, handlerSpan.getStatus().getStatusCode());
            assertEquals(handlerSpan.getSpanId(), currentDuringHandle.getSpanContext().getSpanId(),
                         "the handler span must have been current during handleRequest");
        }
        serverSpan.end();

        tracerProvider.close();
    }

    @Test
    void handler_span_parents_correctly_on_a_real_worker_thread() throws InterruptedException {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        Telemetry telemetry = recording(tracerProvider);
        Span serverSpan = telemetry.tracer(OtelTracing.DEFAULT_SCOPE).startSpan("server", SpanKind.SERVER, Context.root());
        Context bridged = Telemetry.store(Context.root().with(serverSpan), telemetry);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CapturingHandler handler = new CapturingHandler(executor);   // real worker thread, not the test thread
        try (RequestHandlerTestDriver driver = new RequestHandlerTestDriver(handler)) {
            Request request = driver.createRequest("http://localhost/foo", HttpRequest.Method.GET);
            request.context().put(RequestUtils.JDISC_REQUEST_OTEL_CONTEXT, bridged);
            driver.sendRequest(request, "");
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "the handler task must complete");
        }
        serverSpan.end();

        SpanData handlerSpan = spanNamed(exporter, "handler.CapturingHandler");
        assertEquals(serverSpan.getSpanContext().getSpanId(), handlerSpan.getParentSpanId(),
                     "the handler span must be a child of the bridged SERVER span");
        assertEquals(handlerSpan.getSpanId(), handler.current.get().getSpanContext().getSpanId(),
                     "the span made current on the real worker thread must be the handler span");

        tracerProvider.close();
    }

    private static Telemetry recording(SdkTracerProvider tracerProvider) {
        return new Telemetry() {
            @Override public ScopedTracer tracer(String scope) { return new ScopedTracer(tracerProvider.get(scope)); }
            @Override public TextMapPropagator textMapPropagator() { return NoopTelemetry.INSTANCE.textMapPropagator(); }
        };
    }

    private static SpanData spanNamed(InMemorySpanExporter exporter, String name) {
        return exporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no span named '" + name + "' in " + exporter.getFinishedSpanItems()));
    }

    /** Captures the current span during handleRequest and returns an empty 200. */
    private static final class CapturingHandler extends ThreadedRequestHandler {
        final AtomicReference<Span> current = new AtomicReference<>();

        CapturingHandler(Executor executor) { super(executor); }

        @Override
        protected void handleRequest(Request request, BufferedContentChannel requestContent, ResponseHandler responseHandler) {
            current.set(Span.current());
            ResponseDispatch.newInstance(new Response(Response.Status.OK)).dispatch(responseHandler);
        }
    }

    /** Throws from handleRequest to exercise the error path. */
    private static final class ThrowingHandler extends ThreadedRequestHandler {
        ThrowingHandler(Executor executor) { super(executor); }

        @Override
        protected void handleRequest(Request request, BufferedContentChannel requestContent, ResponseHandler responseHandler) {
            throw new RuntimeException("boom");
        }
    }

    /** Allows an async response: captures the current span and the response handler, then responds later. */
    private static final class AsyncHandler extends ThreadedRequestHandler {
        final AtomicReference<Span> current = new AtomicReference<>();
        private volatile ResponseHandler responseHandler;

        AsyncHandler(Executor executor) { super(executor, null, true); }

        @Override
        protected void handleRequest(Request request, BufferedContentChannel requestContent, ResponseHandler responseHandler) {
            current.set(Span.current());
            this.responseHandler = responseHandler;   // respond later, not in this call
        }

        void respond() {
            ResponseHandler rh = responseHandler;
            if (rh != null)
                ResponseDispatch.newInstance(new Response(Response.Status.OK)).dispatch(rh);
        }
    }
}
