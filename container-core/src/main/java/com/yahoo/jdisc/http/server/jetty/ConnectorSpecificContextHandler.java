// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ContextHandler;

import java.util.List;

/**
 * @author bjorncs
 */
class ConnectorSpecificContextHandler extends ContextHandler {

    private final JDiscServerConnector connector;

    ConnectorSpecificContextHandler(JDiscServerConnector c, Handler handler) {
        super(handler);
        this.connector = c;
        List<String> allowedServerNames = c.connectorConfig().serverName().allowed();
        if (allowedServerNames.isEmpty()) {
            setVirtualHosts(List.of("@%s".formatted(c.getName())));
        } else {
            var virtualHosts = allowedServerNames.stream()
                    .map(name -> "%s@%s".formatted(name, c.getName()))
                    .toList();
            setVirtualHosts(virtualHosts);
        }
    }

    @Override
    public boolean checkVirtualHost(Request req) {
        // Accept health checks independently of virtual host configuration when connector matches
        if (req.getHttpURI().getPath().equals("/status.html") && req.getConnectionMetaData().getConnector() == connector)
            return true;
        return super.checkVirtualHost(req);
    }
}
