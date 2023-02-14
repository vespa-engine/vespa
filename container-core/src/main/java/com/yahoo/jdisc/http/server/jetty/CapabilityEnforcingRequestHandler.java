// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.jdisc.RequestView;
import com.yahoo.container.jdisc.utils.CapabilityRequiringRequestHandler;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.DelegatedRequestHandler;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.security.tls.MissingCapabilitiesException;
import com.yahoo.security.tls.TransportSecurityUtils;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Optional;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Request handler wrapper that enforces required capabilities as specified by underlying {@link CapabilityRequiringRequestHandler}.
 *
 * @author bjorncs
 */
class CapabilityEnforcingRequestHandler implements DelegatedRequestHandler {

    private final RequestHandler wrapped;

    CapabilityEnforcingRequestHandler(RequestHandler wrapped) { this.wrapped = wrapped; }

    @Override
    public ContentChannel handleRequest(Request req, ResponseHandler responseHandler) {
        var capabilityRequiringHandler =
                DelegatedRequestHandler.resolve(CapabilityRequiringRequestHandler.class, wrapped).orElse(null);
        var requiredCapabilities = capabilityRequiringHandler != null
                ? capabilityRequiringHandler.requiredCapabilities(new View(req))
                : CapabilityRequiringRequestHandler.DEFAULT_REQUIRED_CAPABILITY.toCapabilitySet();
        var authCtx = Optional.ofNullable(req.context().get(RequestUtils.JDISC_REQUEST_SSLSESSION))
                .flatMap(s -> TransportSecurityUtils.getConnectionAuthContext((SSLSession) s))
                .orElse(null);

        // Connection auth context will not be available if handler is bound to:
        // 1) server with custom TLS configuration.
        // 2) server without TLS (http)
        if (authCtx != null) {
            var peer = Optional.ofNullable(((HttpRequest)req).getRemoteAddress())
                    .map(Object::toString).orElse("<unknown>");
            String method = ((HttpRequest) req).getMethod().name();
            try {
                authCtx.verifyCapabilities(requiredCapabilities, method, req.getUri().getPath(), peer);
            } catch (MissingCapabilitiesException e) {
                int code = HttpResponse.Status.FORBIDDEN;
                var resp = new Response(code);
                resp.headers().add("Content-Type", "application/json");
                ContentChannel ch = responseHandler.handleResponse(resp);
                var slime = new Slime();
                var root = slime.setObject();
                root.setString("error-code", Integer.toString(code));
                root.setString("message", "Missing required capabilities");
                ch.write(ByteBuffer.wrap(uncheck(() -> SlimeUtils.toJsonBytes(slime))), null);
                ch.close(null);
                return null;
            }
        }
        return wrapped.handleRequest(req, responseHandler);
    }

    @Override public void release() { wrapped.release(); }
    @Override public RequestHandler getDelegate() { return wrapped; }
    @Override public void handleTimeout(Request request, ResponseHandler handler) { wrapped.handleRequest(request, handler); }
    @Override public ResourceReference refer() { return wrapped.refer(); }
    @Override public ResourceReference refer(Object context) { return wrapped.refer(context); }

    private static class View implements RequestView {
        private final HttpRequest req;
        View(Request req) { this.req = (HttpRequest) req; }
        @Override public HttpRequest.Method method() { return req.getMethod(); }
        @Override public URI uri() { return req.getUri(); }
    }

}
