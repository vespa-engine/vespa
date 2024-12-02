// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.handler.OverloadException;
import com.yahoo.jdisc.http.HttpRequest.Method;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.jdisc.http.server.jetty.RequestUtils.getConnector;

/**
 * @author Simon Thoresen Hult
 * @author bjorncs
 */
@WebServlet(asyncSupported = true, description = "Bridge between Servlet and JDisc APIs")
class JDiscHttpServlet extends HttpServlet {

    public static final String ATTRIBUTE_NAME_ACCESS_LOG_ENTRY = JDiscHttpServlet.class.getName() + "_access-log-entry";

    private final static Logger log = Logger.getLogger(JDiscHttpServlet.class.getName());

    private static final Set<String> servletSupportedMethods =
            Stream.of(Method.OPTIONS, Method.GET, Method.HEAD, Method.POST, Method.PUT, Method.DELETE, Method.TRACE)
                  .map(Method::name)
                  .collect(Collectors.toSet());

    private final Supplier<JDiscContext> context;

    public JDiscHttpServlet(Supplier<JDiscContext> context) {
        this.context = context;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatchHttpRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatchHttpRequest(request, response);
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatchHttpRequest(request, response);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatchHttpRequest(request, response);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatchHttpRequest(request, response);
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatchHttpRequest(request, response);
    }

    @Override
    protected void doTrace(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatchHttpRequest(request, response);
    }

    /**
     * Override to set connector attribute before the request becomes an upgrade request in the web socket case.
     * (After the upgrade, the HttpConnection is no longer available.)
     */
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        request.setAttribute(JDiscServerConnector.REQUEST_ATTRIBUTE, getConnector((Request) request));

        Metric.Context metricContext = getMetricContext(request);
        context.get().metric().add(MetricDefinitions.NUM_REQUESTS, 1, metricContext);
        context.get().metric().add(MetricDefinitions.JDISC_HTTP_REQUESTS, 1, metricContext);

        String method = request.getMethod().toUpperCase();
        if (servletSupportedMethods.contains(method)) {
            super.service(request, response);
        } else if (method.equals(Method.PATCH.name())) {
            // PATCH method is not handled by the Servlet spec
            dispatchHttpRequest(request, response, metricContext);
        } else {
            // Divergence from HTTP / Servlet spec: JDisc returns 405 for both unknown and known (but unsupported) methods.
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    private void dispatchHttpRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatchHttpRequest(request, response, getMetricContext(request));
    }

    private void dispatchHttpRequest(HttpServletRequest request, HttpServletResponse response, Metric.Context metricContext) throws IOException {
        AccessLogEntry accessLogEntry = new AccessLogEntry();
        request.setAttribute(ATTRIBUTE_NAME_ACCESS_LOG_ENTRY, accessLogEntry);
        try {
            switch (request.getDispatcherType()) {
                case REQUEST:
                    new HttpRequestDispatch(context.get(), accessLogEntry, metricContext, request, response).dispatchRequest();
                    break;
                default:
                    if (log.isLoggable(Level.INFO)) {
                        log.info("Unexpected " + request.getDispatcherType() + "; " + formatAttributes(request));
                    }
                    break;
            }
        } catch (OverloadException e) {
            // nop
        } catch (RuntimeException e) {
            throw new ExceptionWrapper(e);
        }
    }

    private static Metric.Context getMetricContext(HttpServletRequest request) {
        return JDiscServerConnector.fromRequest(request).createRequestMetricContext(request, Map.of());
    }

    private static String formatAttributes(final HttpServletRequest request) {
        StringBuilder out = new StringBuilder();
        out.append("attributes = {");
        for (Enumeration<String> names = request.getAttributeNames(); names.hasMoreElements(); ) {
            String name = names.nextElement();
            out.append(" '").append(name).append("' = '").append(request.getAttribute(name)).append("'");
            if (names.hasMoreElements()) {
                out.append(",");
            }
        }
        out.append(" }");
        return out.toString();
    }
}
