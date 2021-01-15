// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.ConnectionLogEntry;
import com.yahoo.jdisc.http.ServerConfig;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.ssl.SslConnection;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Jetty integration for jdisc connection log ({@link ConnectionLog}).
 *
 * @author bjorncs
 */
class JettyConnectionLogger extends AbstractLifeCycle implements Connection.Listener, HttpChannel.Listener {

    static final String CONNECTION_ID_REQUEST_ATTRIBUTE = "jdisc.request.connection.id";

    private static final Logger log = Logger.getLogger(JettyConnectionLogger.class.getName());

    private final ConcurrentMap<Connection, AggregatedConnectionInfo> connectionInfo = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final ConnectionLog connectionLog;

    JettyConnectionLogger(ServerConfig.ConnectionLog config, ConnectionLog connectionLog) {
        this.enabled = config.enabled();
        this.connectionLog = connectionLog;
        log.log(Level.FINE, () -> "Jetty connection logger is " + (config.enabled() ? "enabled" : "disabled"));
    }

    //
    // AbstractLifeCycle methods start
    //
    @Override
    protected void doStop() {
        handleListenerInvocation("AbstractLifeCycle", "doStop", "", List.of(), () -> {});
        log.log(Level.FINE, () -> "Jetty connection logger is stopped");
    }

    @Override
    protected void doStart() {
        handleListenerInvocation("AbstractLifeCycle", "doStart", "", List.of(), () -> {});
        log.log(Level.FINE, () -> "Jetty connection logger is started");
    }
    //
    // AbstractLifeCycle methods stop
    //

    //
    // Connection.Listener methods start
    //
    @Override
    public void onOpened(Connection connection) {
        handleListenerInvocation("Connection.Listener", "onOpened", "%h", List.of(connection), () -> {
            AggregatedConnectionInfo info = new AggregatedConnectionInfo(UUID.randomUUID());
            synchronized (info.lock()) {
                EndPoint endpoint = connection.getEndPoint();
                info.setCreatedAt(endpoint.getCreatedTimeStamp())
                        .setLocalAddress(endpoint.getLocalAddress())
                        .setPeerAddress(endpoint.getRemoteAddress());
                if (connection instanceof SslConnection) {
                    SslConnection sslConnection = (SslConnection) connection;
                    SSLEngine sslEngine = sslConnection.getSSLEngine();
                    SSLSession sslSession = sslEngine.getSession();
                    info.setSslSessionDetails(sslSession);
                }
            }
            connectionInfo.put(connection, info);
        });
    }

    @Override
    public void onClosed(Connection connection) {
        handleListenerInvocation("Connection.Listener", "onClosed", "%h", List.of(connection), () -> {
            // TODO Decide on handling of connection upgrade where old connection object is closed and replaced by a new (e.g for proxy-protocol auto detection)
            AggregatedConnectionInfo builder = Objects.requireNonNull(connectionInfo.remove(connection));
            ConnectionLogEntry logEntry;
            synchronized (builder.lock()) {
                logEntry = builder.setBytesReceived(connection.getBytesIn())
                        .setBytesSent(connection.getBytesOut())
                        .toLogEntry();
            }
            connectionLog.log(logEntry);
        });
    }
    //
    // Connection.Listener methods end
    //

    //
    // HttpChannel.Listener methods start
    //
    @Override
    public void onRequestBegin(Request request) {
        handleListenerInvocation("HttpChannel.Listener", "onRequestBegin", "%h", List.of(request), () -> {
            Connection connection = request.getHttpChannel().getConnection();
            AggregatedConnectionInfo info = Objects.requireNonNull(connectionInfo.get(connection));
            UUID uuid;
            synchronized (info.lock()) {
                info.incrementRequests();
                uuid = info.uuid();
            }
            request.setAttribute(CONNECTION_ID_REQUEST_ATTRIBUTE, uuid);
        });
    }

    @Override
    public void onResponseBegin(Request request) {
        handleListenerInvocation("HttpChannel.Listener", "onResponseBegin", "%h", List.of(request), () -> {
            Connection connection = request.getHttpChannel().getConnection();
            AggregatedConnectionInfo info = Objects.requireNonNull(connectionInfo.get(connection));
            synchronized (info.lock()) {
                info.incrementResponses();
            }
        });
    }
    //
    // HttpChannel.Listener methods end
    //

    private void handleListenerInvocation(
            String listenerType, String methodName, String methodArgumentsFormat, List<Object> methodArguments, ListenerHandler handler) {
        if (!enabled) return;
        try {
            log.log(Level.FINE, () -> String.format(listenerType + "." + methodName + "(" + methodArgumentsFormat + ")", methodArguments.toArray()));
            handler.run();
        } catch (Exception e) {
            log.log(Level.WARNING, String.format("Exception in %s.%s listener: %s", listenerType, methodName, e.getMessage()), e);
        }
    }

    @FunctionalInterface private interface ListenerHandler { void run() throws Exception; }

    private static class AggregatedConnectionInfo {
        private final Object monitor = new Object();

        private final UUID uuid;

        private long createdAt;
        private InetSocketAddress localAddress;
        private InetSocketAddress peerAddress;
        private long bytesReceived;
        private long bytesSent;
        private long requests;
        private long responses;
        private byte[] sslSessionId;
        private String sslProtocol;
        private String sslCipherSuite;
        private String sslPeerSubject;
        private Date sslPeerNotBefore;
        private Date sslPeerNotAfter;
        private List<SNIServerName> sslSniServerNames;

        AggregatedConnectionInfo(UUID uuid) {
            this.uuid = uuid;
        }

        Object lock() { return monitor; }

        UUID uuid() { return uuid; }

        AggregatedConnectionInfo setCreatedAt(long createdAt) { this.createdAt = createdAt; return this; }

        AggregatedConnectionInfo setLocalAddress(InetSocketAddress localAddress) { this.localAddress = localAddress; return this; }

        AggregatedConnectionInfo setPeerAddress(InetSocketAddress peerAddress) { this.peerAddress = peerAddress; return this; }

        AggregatedConnectionInfo setBytesReceived(long bytesReceived) { this.bytesReceived = bytesReceived; return this; }

        AggregatedConnectionInfo setBytesSent(long bytesSent) { this.bytesSent = bytesSent; return this; }

        AggregatedConnectionInfo incrementRequests() { ++this.requests; return this; }

        AggregatedConnectionInfo incrementResponses() { ++this.responses; return this; }

        AggregatedConnectionInfo setSslSessionDetails(SSLSession session) {
            this.sslCipherSuite = session.getCipherSuite();
            this.sslProtocol = session.getProtocol();
            this.sslSessionId = session.getId();
            if (session instanceof ExtendedSSLSession) {
                ExtendedSSLSession extendedSession = (ExtendedSSLSession) session;
                this.sslSniServerNames = extendedSession.getRequestedServerNames();
            }
            try {
                this.sslPeerSubject = session.getPeerPrincipal().getName();
                X509Certificate peerCertificate = (X509Certificate) session.getPeerCertificates()[0];
                this.sslPeerNotBefore = peerCertificate.getNotAfter();
                this.sslPeerNotAfter = peerCertificate.getNotAfter();
            } catch (SSLPeerUnverifiedException e) {
                // Throw if peer is not authenticated (e.g when client auth is disabled)
                // JSSE provides no means of checking for client authentication without catching this exception
            }
            return this;
        }

        ConnectionLogEntry toLogEntry() {
            return ConnectionLogEntry.builder(uuid, Instant.ofEpochMilli(createdAt))
                    .withPeerAddress(peerAddress.getHostString())
                    .withPeerPort(peerAddress.getPort())
                    .build();
        }

    }
}
