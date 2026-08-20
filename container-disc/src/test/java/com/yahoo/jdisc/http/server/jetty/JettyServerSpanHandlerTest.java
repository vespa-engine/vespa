// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import ai.vespa.telemetry.api.NoopTelemetry;
import ai.vespa.telemetry.api.Telemetry;
import ai.vespa.telemetry.api.trace.ScopedTracer;
import com.google.inject.Module;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.OK;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link JettyServerSpanHandler} produces an OpenTelemetry server span per HTTP request,
 * with the expected semantic-convention attributes and remote-parent extraction.
 *
 * @author onur
 */
class JettyServerSpanHandlerTest {

    private static final AttributeKey<String> HTTP_REQUEST_METHOD       = AttributeKey.stringKey("http.request.method");
    private static final AttributeKey<Long>   HTTP_RESPONSE_STATUS_CODE = AttributeKey.longKey("http.response.status_code");
    private static final AttributeKey<String> HTTP_ROUTE                = AttributeKey.stringKey("http.route");
    private static final AttributeKey<String> URL_SCHEME                = AttributeKey.stringKey("url.scheme");
    private static final AttributeKey<String> SERVER_ADDRESS            = AttributeKey.stringKey("server.address");
    private static final AttributeKey<String> NETWORK_PROTOCOL_VERSION  = AttributeKey.stringKey("network.protocol.version");
    private static final AttributeKey<String> ERROR_TYPE                = AttributeKey.stringKey("error.type");

    @Test
    void serverSpanIsCreatedWithSemconvAttributes() throws IOException, InterruptedException {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        JettyTestDriver driver = JettyTestDriver.newInstance(new EchoRequestHandler(), otelModule(exporter));

        driver.client().newGet("/search/").execute().expectStatusCode(is(OK));

        SpanData span = awaitSingleSpan(exporter);
        assertEquals(SpanKind.SERVER, span.getKind());
        assertEquals("GET /search", span.getName());
        Attributes attrs = span.getAttributes();
        assertEquals("GET", attrs.get(HTTP_REQUEST_METHOD));
        assertEquals("/search", attrs.get(HTTP_ROUTE));
        assertEquals("http", attrs.get(URL_SCHEME));
        assertEquals(Long.valueOf(200), attrs.get(HTTP_RESPONSE_STATUS_CODE));
        assertNotNull(attrs.get(SERVER_ADDRESS));
        assertNotNull(attrs.get(NETWORK_PROTOCOL_VERSION));

        assertTrue(driver.close());
    }

    @Test
    void serverSpanExtractsRemoteParent() throws IOException, InterruptedException {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        JettyTestDriver driver = JettyTestDriver.newInstance(new EchoRequestHandler(), otelModule(exporter));

        String traceId = "0af7651916cd43dd8448eb211c80319c";
        String parentSpanId = "b7ad6b7169203331";
        driver.client().newGet("/search/")
                .addHeader("traceparent", "00-" + traceId + "-" + parentSpanId + "-01")
                .execute().expectStatusCode(is(OK));

        SpanData span = awaitSingleSpan(exporter);
        assertEquals(traceId, span.getSpanContext().getTraceId());
        assertEquals(parentSpanId, span.getParentSpanContext().getSpanId());

        assertTrue(driver.close());
    }

    @Test
    void serverSpanMarksErrorOnServerError() throws IOException, InterruptedException {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        JettyTestDriver driver = JettyTestDriver.newInstance(new ServerErrorRequestHandler(), otelModule(exporter));

        driver.client().newGet("/search/").execute().expectStatusCode(is(INTERNAL_SERVER_ERROR));

        SpanData span = awaitSingleSpan(exporter);
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        assertEquals("500", span.getAttributes().get(ERROR_TYPE));
        assertEquals(Long.valueOf(500), span.getAttributes().get(HTTP_RESPONSE_STATUS_CODE));

        assertTrue(driver.close());
    }

    @Test
    void requestSucceedsWithNoopTelemetry() throws IOException {
        // No Telemetry override, so TestDriver's default NoopTelemetry binding is used (telemetry disabled).
        // The handler is still installed but produces no spans; the request must be unaffected.
        JettyTestDriver driver = JettyTestDriver.newInstance(new EchoRequestHandler());

        driver.client().newGet("/search/").execute().expectStatusCode(is(OK));

        assertTrue(driver.close());
    }

    @Test
    void bridgedContextCarriesTelemetryWhenEnabled() throws IOException {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        Telemetry telemetry = recordingTelemetry(exporter);
        AtomicReference<Context> captured = new AtomicReference<>();
        JettyTestDriver driver = JettyTestDriver.newInstance(contextCapturingHandler(captured), moduleFor(telemetry));

        driver.client().newGet("/search/").execute().expectStatusCode(is(OK));

        Context ctx = captured.get();
        assertNotNull(ctx, "the SERVER context must reach the handler thread via the jdisc request context");
        assertTrue(Span.fromContext(ctx).getSpanContext().isValid(), "a recording server span must be set on it");
        assertSame(telemetry, Telemetry.from(ctx), "the enabled Telemetry must ride along in the context");

        assertTrue(driver.close());
    }

    @Test
    void bridgedContextCarriesNoTelemetryWhenDisabled() throws IOException {
        // Default NoopTelemetry binding (disabled). The context is still bridged (Decision 3A), but store()
        // short-circuited, so no telemetry rides along and the server span is invalid/non-recording.
        AtomicReference<Context> captured = new AtomicReference<>();
        JettyTestDriver driver = JettyTestDriver.newInstance(contextCapturingHandler(captured));

        driver.client().newGet("/search/").execute().expectStatusCode(is(OK));

        Context ctx = captured.get();
        assertNotNull(ctx, "the context is bridged unconditionally (Decision 3A)");
        assertFalse(Span.fromContext(ctx).getSpanContext().isValid(), "the disabled server span is invalid");
        assertSame(NoopTelemetry.INSTANCE, Telemetry.from(ctx), "no telemetry rides along when disabled");

        assertTrue(driver.close());
    }

    private static Module otelModule(InMemorySpanExporter exporter) {
        return moduleFor(recordingTelemetry(exporter));
    }

    private static Telemetry recordingTelemetry(InMemorySpanExporter exporter) {
        OpenTelemetry sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        // Minimal recording Telemetry over the in-memory SDK (OtelTelemetry.create builds its own SDK, so it
        // cannot be pointed at an InMemorySpanExporter; the seam is trivial to implement inline for the test).
        return new Telemetry() {
            @Override public ScopedTracer tracer(String scope) { return new ScopedTracer(sdk.getTracer(scope)); }
            @Override public TextMapPropagator textMapPropagator() { return sdk.getPropagators().getTextMapPropagator(); }
        };
    }

    private static Module moduleFor(Telemetry telemetry) {
        return binder -> binder.bind(Telemetry.class).toInstance(telemetry);
    }

    /** {@code onComplete} fires just after the client receives the response, so poll briefly for the span. */
    private static SpanData awaitSingleSpan(InMemorySpanExporter exporter) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        List<SpanData> spans;
        while ((spans = exporter.getFinishedSpanItems()).isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, spans.size(), "expected exactly one server span");
        return spans.get(0);
    }

    private static AbstractRequestHandler contextCapturingHandler(AtomicReference<Context> captured) {
        return new AbstractRequestHandler() {
            @Override
            public ContentChannel handleRequest(Request request, ResponseHandler handler) {
                if (request.context().get(RequestUtils.JDISC_REQUEST_OTEL_CONTEXT) instanceof Context ctx) captured.set(ctx);
                return handler.handleResponse(new Response(OK));
            }
        };
    }

    private static class ServerErrorRequestHandler extends AbstractRequestHandler {
        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            return handler.handleResponse(new Response(INTERNAL_SERVER_ERROR));
        }
    }

}
