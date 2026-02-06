// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.Container;
import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.RequestLog;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.service.CurrentContainer;

/**
 * @author jonmv
 *
 * Context that a {@link JettyHttpServer} uses to pass requests to JDisc.
 * This registers itself with the server upon construction, and unregisters
 * on deconstruction, and the server always uses the most recent context.
 */
public class JettyHttpServerContext extends AbstractComponent {

    private final JDiscContext context;
    private final JettyHttpServer server;

    @Inject // Must use guice annotation due to setup in TestDriver
    public JettyHttpServerContext(CurrentContainer container, Metric metric, FilterBindings filterBindings,
                                  Janitor janitor, JettyHttpServer server) {
        this.server = server;
        this.context = server.registerContext(filterBindings, container, janitor, metric);
    }

    @Override
    public void deconstruct() {
        server.deregisterContext(context);
    }

}
