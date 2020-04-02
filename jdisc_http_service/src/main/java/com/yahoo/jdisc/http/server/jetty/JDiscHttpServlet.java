// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.handler.OverloadException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.jdisc.http.core.HttpServletRequestUtils.getConnection;

/**
 * @author Simon Thoresen Hult
 * @author bjorncs
 */
@WebServlet(asyncSupported = true, description = "Bridge between Servlet and JDisc APIs")
class JDiscHttpServlet extends HttpServlet {

    public static final String ATTRIBUTE_NAME_ACCESS_LOG_ENTRY = JDiscHttpServlet.class.getName() + "_access-log-entry";

    private final static Logger log = Logger.getLogger(JDiscHttpServlet.class.getName());
    private final JDiscContext context;

    public JDiscHttpServlet(JDiscContext context) {
        this.context = context;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        dispatchHttpRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        dispatchHttpRequest(request, response);
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        dispatchHttpRequest(request, response);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        dispatchHttpRequest(request, response);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        dispatchHttpRequest(request, response);
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        dispatchHttpRequest(request, response);
    }

    @Override
    protected void doTrace(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        dispatchHttpRequest(request, response);
    }

    private static final Set<String> JETTY_UNSUPPORTED_METHODS = new HashSet<>(Arrays.asList("PATCH"));

    /**
     * Override to set connector attribute before the request becomes an upgrade request in the web socket case.
     * (After the upgrade, the HttpConnection is no longer available.)
     */
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        request.setAttribute(JDiscServerConnector.REQUEST_ATTRIBUTE, getConnector(request));

        Metric.Context metricContext = getMetricContext(request);
        context.metric.add(JettyHttpServer.Metrics.NUM_REQUESTS, 1, metricContext);
        context.metric.add(JettyHttpServer.Metrics.JDISC_HTTP_REQUESTS, 1, metricContext);

        if (JETTY_UNSUPPORTED_METHODS.contains(request.getMethod().toUpperCase())) {
            dispatchHttpRequest(request, response);
        } else {
            super.service(request, response);
        }
    }



    static JDiscServerConnector getConnector(HttpServletRequest request) {
        return (JDiscServerConnector)getConnection(request).getConnector();
    }

    private void dispatchHttpRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AccessLogEntry accessLogEntry = new AccessLogEntry();
        request.setAttribute(ATTRIBUTE_NAME_ACCESS_LOG_ENTRY, accessLogEntry);
        try {
            switch (request.getDispatcherType()) {
                case REQUEST:
                    new HttpRequestDispatch(context, accessLogEntry, getMetricContext(request), request, response)
                            .dispatch();
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
        return JDiscServerConnector.fromRequest(request).getRequestMetricContext(request);
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
