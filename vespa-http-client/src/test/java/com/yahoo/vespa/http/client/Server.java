// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 * @author Einar M R Rosenvinge
 */
public final class Server implements AutoCloseable {

    private final org.eclipse.jetty.server.Server server;

    public Server(AbstractHandler handler, int port) {
        this.server = new org.eclipse.jetty.server.Server(port);
        server.setHandler(handler);
        try {
            server.start();
            assert(server.isStarted());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws RuntimeException {
        try {
            server.stop();
        } catch (RuntimeException e) {
	    throw e;
	} catch (Exception e) {
            throw new RuntimeException("jetty server.stop() failed", e);
        }
    }

    public int getPort() {
        return ((ServerConnector)server.getConnectors()[0]).getLocalPort();
    }
}
