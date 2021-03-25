// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.http.ConnectorConfig;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.URIUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.yahoo.jdisc.http.server.jetty.HttpServletRequestUtils.getConnectorLocalPort;

/**
 * A secure redirect handler inspired by {@link org.eclipse.jetty.server.handler.SecuredRedirectHandler}.
 *
 * @author bjorncs
 */
class SecuredRedirectHandler extends HandlerWrapper {

    private static final String HEALTH_CHECK_PATH = "/status.html";

    private final Map<Integer, Integer> redirectMap;

    SecuredRedirectHandler(List<ConnectorConfig> connectorConfigs) {
        this.redirectMap = createRedirectMap(connectorConfigs);
    }

    @Override
    public void handle(String target, Request request, HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws IOException, ServletException {
        int localPort = getConnectorLocalPort(servletRequest);
        if (!redirectMap.containsKey(localPort)) {
            _handler.handle(target, request, servletRequest, servletResponse);
            return;
        }
        servletResponse.setContentLength(0);
        if (!servletRequest.getRequestURI().equals(HEALTH_CHECK_PATH)) {
            servletResponse.sendRedirect(
                    URIUtil.newURI("https", request.getServerName(), redirectMap.get(localPort), request.getRequestURI(), request.getQueryString()));
        }
        request.setHandled(true);
    }

    private static Map<Integer, Integer> createRedirectMap(List<ConnectorConfig> connectorConfigs) {
        var redirectMap = new HashMap<Integer, Integer>();
        for (ConnectorConfig connectorConfig : connectorConfigs) {
            if (connectorConfig.secureRedirect().enabled()) {
                redirectMap.put(connectorConfig.listenPort(), connectorConfig.secureRedirect().port());
            }
        }
        return redirectMap;
    }
}
