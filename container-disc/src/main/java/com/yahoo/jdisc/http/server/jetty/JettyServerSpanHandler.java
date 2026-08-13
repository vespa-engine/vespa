// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import ai.vespa.telemetry.api.Telemetry;
import ai.vespa.telemetry.api.trace.ScopedTracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.EventsHandler;

import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Jetty integration that creates an OpenTelemetry {@link SpanKind#SERVER server span} per HTTP request.
 *
 * <p>The span is started when request handling begins ({@link #onBeforeHandling}) and ended when the
 * request and response are fully processed ({@link #onComplete}). The owning {@link Context} (the
 * incoming W3C trace context with this server span set) is stored as a Jetty request attribute under
 * {@link #OTEL_CONTEXT_REQUEST_ATTRIBUTE}, so downstream code can create child spans and propagate it.</p>
 *
 * <p>{@link #onBeforeHandling} and {@link #onComplete} are not guaranteed to run on the same thread
 * (async requests), so the span is carried explicitly in the request attribute rather than via a
 * thread-local {@link io.opentelemetry.context.Scope}.</p>
 *
 * <p>Always installed; when telemetry is disabled the injected {@link Telemetry} is
 * {@link ai.vespa.telemetry.api.NoopTelemetry}, so all spans are non-recording and effectively free.</p>
 *
 * @author onur
 */
class JettyServerSpanHandler extends EventsHandler {

    /** Request attribute holding the {@link Context} (incoming context + server span) for this request. */
    static final String OTEL_CONTEXT_REQUEST_ATTRIBUTE = "jdisc.request.otel.context";

    private static final Logger log = Logger.getLogger(JettyServerSpanHandler.class.getName());
    private static final String INSTRUMENTATION_SCOPE_NAME = "com.yahoo.jdisc.http.server.jetty";

    // OpenTelemetry HTTP-server semantic-convention attribute keys. Hardcoded here (rather than depending
    // on the alpha opentelemetry-semconv artifact) so this stays on the API-only classpath.
    private static final AttributeKey<String> HTTP_REQUEST_METHOD       = AttributeKey.stringKey("http.request.method");
    private static final AttributeKey<Long>   HTTP_RESPONSE_STATUS_CODE = AttributeKey.longKey("http.response.status_code");
    private static final AttributeKey<String> HTTP_ROUTE                = AttributeKey.stringKey("http.route");
    private static final AttributeKey<String> URL_SCHEME                = AttributeKey.stringKey("url.scheme");
    private static final AttributeKey<String> SERVER_ADDRESS            = AttributeKey.stringKey("server.address");
    private static final AttributeKey<Long>   SERVER_PORT               = AttributeKey.longKey("server.port");
    private static final AttributeKey<String> CLIENT_ADDRESS            = AttributeKey.stringKey("client.address");
    private static final AttributeKey<String> NETWORK_PROTOCOL_VERSION  = AttributeKey.stringKey("network.protocol.version");
    private static final AttributeKey<String> USER_AGENT_ORIGINAL       = AttributeKey.stringKey("user_agent.original");
    private static final AttributeKey<String> ERROR_TYPE                = AttributeKey.stringKey("error.type");

    private static final TextMapGetter<Request> HEADER_GETTER = new TextMapGetter<>() {
        @Override public Iterable<String> keys(Request request) {
            return request.getHeaders().getFieldNamesCollection();
        }
        @Override public String get(Request request, String key) {
            if (request == null) return null;
            return request.getHeaders().get(key);
        }
    };

    private final ScopedTracer tracer;
    private final TextMapPropagator propagator;

    JettyServerSpanHandler(Telemetry telemetry, Handler handler) {
        super(handler);
        this.tracer = telemetry.tracer(INSTRUMENTATION_SCOPE_NAME);
        this.propagator = telemetry.textMapPropagator();
    }

    /**
     * Starts the per-request SERVER span at the beginning of request handling.
     *
     * <p>Context propagation: the incoming W3C trace-context headers are read via the {@link #propagator}
     * to recover the caller's (remote parent) {@link Context}; the new span is created as a child of it, so a
     * distributed trace started upstream continues through this server. The owning context — the extracted
     * parent with this span set on it ({@code parentContext.with(span)}) — is then stored in the Jetty request
     * attribute {@link #OTEL_CONTEXT_REQUEST_ATTRIBUTE}, NOT in a thread-local {@link io.opentelemetry.context.Scope},
     * because {@link #onComplete} may run on a different thread for async requests. Downstream code can read that
     * attribute to create child spans and propagate the context further.</p>
     *
     * <p>Best-effort: any failure here is logged and swallowed so instrumentation never breaks request handling.</p>
     */
    @Override
    protected void onBeforeHandling(Request request) {
        try {
            Context parentContext = propagator.extract(Context.current(), request, HEADER_GETTER);
            Span span = tracer.startServerSpan(spanName(request), parentContext);
            // Store the context before setting attributes so onComplete can always end the span,
            // even if an attribute getter below throws.
            request.setAttribute(OTEL_CONTEXT_REQUEST_ATTRIBUTE, parentContext.with(span));
            setRequestAttributes(span, request);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to start server span", e);
        }
    }

    /**
     * Ends the SERVER span once the request and response are fully processed.
     *
     * <p>Retrieves the owning {@link Context} from the request attribute set in {@link #onBeforeHandling}
     * (returning early if absent — e.g. span creation failed), records the HTTP response status, and marks the
     * span {@link StatusCode#ERROR} on a transport failure or a 5xx response (recording the exception and an
     * {@code error.type}). The span is always ended. This may run on a different thread than
     * {@link #onBeforeHandling}, which is why the context travels via the request attribute rather than a
     * thread-local.</p>
     *
     * <p>Best-effort: any failure here is logged and swallowed.</p>
     */
    @Override
    protected void onComplete(Request request, int status, HttpFields headers, Throwable failure) {
        try {
            if (!(request.getAttribute(OTEL_CONTEXT_REQUEST_ATTRIBUTE) instanceof Context context)) return;
            Span span = Span.fromContext(context);
            span.setAttribute(HTTP_RESPONSE_STATUS_CODE, status);
            if (failure != null) {
                span.recordException(failure);
                span.setStatus(StatusCode.ERROR);
                span.setAttribute(ERROR_TYPE, failure.getClass().getName());
            } else if (status >= 500) {
                span.setStatus(StatusCode.ERROR);
                span.setAttribute(ERROR_TYPE, Integer.toString(status));
            }
            span.end();
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to end server span", e);
        }
    }

    /**
     * No real {@code http.route} is available at this layer (the jdisc handler binding is resolved further
     * down the chain), so we use the first path segment as a cheap, low-cardinality pseudo-route:
     * {@code "{method} /{first-segment}"}, e.g. {@code "GET /document"}. Falls back to the method alone
     * when no path is available.
     */
    private static String spanName(Request request) {
        try {
            String method = request.getMethod();
            if (method == null) method = "HTTP";
            String segment = firstPathSegment(pathOf(request));
            return segment != null ? method + " " + segment : method;
        } catch (RuntimeException e) {
            return "HTTP";
        }
    }

    private static String firstPathSegment(String path) {
        if (path == null || path.isEmpty()) return null;
        if (!path.startsWith("/")) path = "/" + path;
        int next = path.indexOf('/', 1);
        return next < 0 ? path : path.substring(0, next);
    }

    private static void setRequestAttributes(Span span, Request request) {
        setAttribute(span, HTTP_REQUEST_METHOD, request::getMethod);
        // Coarse first-path-segment pseudo-route (the real http.route is not known at this layer); low cardinality.
        setAttribute(span, HTTP_ROUTE, () -> firstPathSegment(pathOf(request)));
        setAttribute(span, URL_SCHEME, () -> schemeOf(request));
        setAttribute(span, SERVER_ADDRESS, () -> Request.getServerName(request));
        setAttribute(span, SERVER_PORT, () -> Request.getServerPort(request));
        setAttribute(span, CLIENT_ADDRESS, () -> Request.getRemoteAddr(request));
        setAttribute(span, NETWORK_PROTOCOL_VERSION, () -> protocolVersion(request));
        setAttribute(span, USER_AGENT_ORIGINAL, () -> request.getHeaders().get("User-Agent"));
    }

    /** Each Jetty getter is isolated: a getter that throws is skipped, leaving the other attributes intact. */
    private static void setAttribute(Span span, AttributeKey<String> key, Supplier<String> getter) {
        try {
            String value = getter.get();
            if (value != null) span.setAttribute(key, value);
        } catch (RuntimeException ignored) {
            // best-effort: a single failing getter must not abort span instrumentation
        }
    }

    private static void setAttribute(Span span, AttributeKey<Long> key, IntSupplier getter) {
        try {
            span.setAttribute(key, getter.getAsInt());
        } catch (RuntimeException ignored) {
            // best-effort: a single failing getter must not abort span instrumentation
        }
    }

    private static String pathOf(Request request) {
        var uri = request.getHttpURI();
        return uri != null ? uri.getPath() : null;
    }

    private static String schemeOf(Request request) {
        var uri = request.getHttpURI();
        return uri != null ? uri.getScheme() : null;
    }

    private static String protocolVersion(Request request) {
        String protocol = request.getConnectionMetaData().getProtocol(); // e.g. "HTTP/1.1"
        if (protocol == null) return null;
        int slash = protocol.indexOf('/');
        return slash >= 0 ? protocol.substring(slash + 1) : protocol;
    }

}
