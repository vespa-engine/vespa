// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import org.eclipse.jetty.http2.HTTP2Connection;
import org.eclipse.jetty.http2.ISession;
import org.eclipse.jetty.http2.server.HTTP2ServerConnection;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.annotation.Name;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.component.Graceful;
import org.eclipse.jetty.util.component.LifeCycle;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Workaround for <a href="https://github.com/eclipse/jetty.project/issues/8811">eclipse/jetty.project#8811</a>.
 *
 * @author bjorncs
 */
class FixedHTTP2ServerConnectionFactory extends HTTP2ServerConnectionFactory {

    private static final Logger log = Logger.getLogger(FixedHTTP2ServerConnectionFactory.class.getName());

    private final HTTP2SessionContainer originalSessionContainer;
    private final FixedHTTP2SessionContainer fixedSessionContainer;

    FixedHTTP2ServerConnectionFactory(@Name("config") HttpConfiguration config) {
        super(config);
        fixedSessionContainer = new FixedHTTP2SessionContainer();
        originalSessionContainer = getBean(HTTP2SessionContainer.class);
        removeBean(originalSessionContainer);
        addBean(fixedSessionContainer);
    }

    @Override
    public Connection newConnection(Connector connector, EndPoint endPoint) {
        var conn = (HTTP2ServerConnection) super.newConnection(connector, endPoint);
        conn.removeEventListener(originalSessionContainer);
        conn.addEventListener(fixedSessionContainer);
        return conn;
    }

    @ManagedObject("The container of HTTP/2 sessions")
    private static class FixedHTTP2SessionContainer implements Connection.Listener, Graceful, Dumpable {

        private final Object monitor = new Object();
        private final Set<ISession> sessions = new HashSet<>();
        private CompletableFuture<Void> shutdown;

        @Override
        public void onOpened(Connection conn) {
            var session = session(conn);
            boolean shuttingDown;
            synchronized (monitor) {
                sessions.add(session);
                shuttingDown = shutdown != null;
            }
            log.fine(() -> "Added session %s".formatted(session));
            LifeCycle.start(session);
            if (shuttingDown) session.shutdown();
        }

        @Override
        public void onClosed(Connection conn) {
            var session = session(conn);
            boolean removed;
            CompletableFuture<Void> shutdown;
            synchronized (monitor) {
                removed = sessions.remove(session);
                shutdown = this.shutdown != null && sessions.size() == 0 && !this.shutdown.isDone()
                        ? this.shutdown : null;
            }
            log.fine(() -> "Removed session %s".formatted(session));
            if (removed) LifeCycle.stop(session);
            if (shutdown != null) {
                log.fine("Shutdown completed after last session removed");
                shutdown.complete(null);
            }
        }

        @Override
        public void dump(Appendable out, String indent) throws IOException {
            synchronized (monitor) { Dumpable.dumpObjects(out, indent, this, sessions); }
        }

        @Override public CompletableFuture<Void> shutdown() {
            CompletableFuture<Void> shutdown = null;
            ISession[] sessionsToClose = null;
            synchronized (monitor) {
                if (this.shutdown == null) {
                    shutdown = (this.shutdown = new CompletableFuture<>());
                    sessionsToClose = sessions.toArray(ISession[]::new);
                }
            }
            if (sessionsToClose != null) {
                log.fine("Shutdown initiated");
                if (sessionsToClose.length > 0) {
                    for (ISession session : sessionsToClose) {
                        session.shutdown();
                    }
                } else {
                    log.fine("Shutdown completed since no sessions");
                    shutdown.complete(null);
                }
            }
            return shutdown;
        }

        @Override public boolean isShutdown() { synchronized (monitor) { return shutdown != null; } }

        private static ISession session(Connection conn) { return ((HTTP2Connection)conn).getSession(); }
    }
}
