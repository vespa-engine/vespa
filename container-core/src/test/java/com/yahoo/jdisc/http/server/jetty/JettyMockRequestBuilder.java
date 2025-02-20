// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.http.ConnectorConfig;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.server.ConnectionMetaData;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.internal.HttpChannelState;
import org.eclipse.jetty.server.internal.HttpConnection;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Builder for creating a mock instance of Jetty's {@link Request} type.
 *
 * @author bjorncs
 */
public class JettyMockRequestBuilder {

    private final Map<String, List<String>> parameters = new HashMap<>();
    private final Map<String, List<String>> headers = new HashMap<>();
    private final Map<String, Object> attributes = new HashMap<>();
    private Integer localPort;
    private String uriScheme;
    private String uriServerName;
    private Integer uriPort;
    private String uriPath;
    private String uriQuery;
    private String remoteAddress;
    private String remoteHost;
    private Integer remotePort;
    private String method;
    private String protocol;

    private JettyMockRequestBuilder() {}

    public static JettyMockRequestBuilder newBuilder() { return new JettyMockRequestBuilder(); }

    public JettyMockRequestBuilder localPort(int localPort) { this.localPort = localPort; return this; }

    public JettyMockRequestBuilder remote(String address, String host, int port) {
        this.remoteAddress = address;
        this.remoteHost = host;
        this.remotePort = port;
        return this;
    }

    public JettyMockRequestBuilder uri(String scheme, String serverName, int port, String path, String query) {
        this.uriScheme = scheme;
        this.uriServerName = serverName;
        this.uriPort = port;
        this.uriPath = path;
        this.uriQuery = query;
        return this;
    }

    public JettyMockRequestBuilder parameter(String name, List<String> values) { this.parameters.put(name, List.copyOf(values)); return this; }

    public JettyMockRequestBuilder header(String name, List<String> values) { this.headers.put(name, List.copyOf(values)); return this; }

    public JettyMockRequestBuilder attribute(String name, Object value) { this.attributes.put(name, value); return this; }

    public JettyMockRequestBuilder method(String method) { this.method = method; return this; }

    public JettyMockRequestBuilder protocol(String protocol) { this.protocol = protocol; return this; }

    public Request build() {
        int localPort = this.localPort != null ? this.localPort : 8080;
        HttpConnection connection = mock(HttpConnection.class);
        JDiscServerConnector connector = mock(JDiscServerConnector.class);
        when(connector.connectorConfig()).thenReturn(new ConnectorConfig(
                new ConnectorConfig.Builder().listenPort(localPort)
                        .accessLog(new ConnectorConfig.AccessLog.Builder()
                                           .remoteAddressHeaders(List.of("x-forwarded-for", "y-ra"))
                                           .remotePortHeaders(List.of("X-Forwarded-Port", "y-rp")))));
        when(connector.getLocalPort()).thenReturn(localPort);
        when(connection.getCreatedTimeStamp()).thenReturn(System.currentTimeMillis());
        when(connection.getConnector()).thenReturn(connector);
        return new DummyRequest(this, connector, connection);
    }

    private static class DummyRequest extends Request.Wrapper {
        private final HttpFields headers;
        private final Map<String, Object> attributes;
        private final HttpURI uri;
        private final String method;
        private final ConnectionMetaData connMetaData;
        private final HttpChannelState.ChannelRequest wrapped;

        DummyRequest(JettyMockRequestBuilder b, JDiscServerConnector connector, Connection connection) {
            super(mock(Request.class, withSettings().stubOnly()));
            int localPort = b.localPort != null ? b.localPort : 8080;
            String scheme = b.uriScheme != null ? b.uriScheme : "http";
            String serverName = b.uriServerName != null ? b.uriServerName : "localhost";
            int uriPort = b.uriPort != null ? b.uriPort : 8080;
            String path = b.uriPath;
            String query = b.uriQuery;

            var method = b.method != null ? b.method : "GET";
            this.uri = HttpURI.from(scheme, serverName, uriPort, path, query, null);
            this.method = method;
            this.connMetaData = new DummyConnectionMetadata(b, connector, connection);
            var mutableFields = HttpFields.build();
            b.headers.forEach(mutableFields::put);
            this.headers = mutableFields;
            this.attributes = new ConcurrentHashMap<>(b.attributes);
            this.wrapped = mock(HttpChannelState.ChannelRequest.class);
            when(wrapped.getContentBytesRead()).thenReturn(2345L);
        }

        @Override public org.eclipse.jetty.server.Request getWrapped() { return wrapped; }
        @Override public HttpURI getHttpURI() { return uri; }
        @Override public String getMethod() { return method; }
        @Override public ConnectionMetaData getConnectionMetaData() { return connMetaData; }
        @Override public HttpFields getHeaders() { return headers; }
        @Override public Object getAttribute(String name) { return attributes.get(name); }
        @Override public Map<String, Object> asAttributeMap() { return attributes; }
        @Override public Set<String> getAttributeNameSet() { return attributes.keySet(); }
        @Override public Object setAttribute(String name, Object attribute) { return attributes.put(name, attribute); }
        @Override public Object removeAttribute(String name) { return attributes.remove(name); }
        @Override public void clearAttributes() { attributes.clear(); }
        @Override public long getHeadersNanoTime() { return System.nanoTime(); }

        private static class DummyConnectionMetadata extends ConnectionMetaData.Wrapper {
            private final String protocol;
            private final JDiscServerConnector connector;
            private final Connection connection;
            private final InetSocketAddress remoteAddress;

            DummyConnectionMetadata(JettyMockRequestBuilder b,
                                    JDiscServerConnector connector,
                                    Connection connection) {
                super(mock(ConnectionMetaData.class, withSettings().stubOnly()));
                this.protocol = b.protocol != null ? b.protocol : "HTTP/1.1";
                this.connector = connector;
                this.connection = connection;
                String remoteAddress = b.remoteAddress != null ? b.remoteAddress : "1.2.3.4";
                String remoteHost = b.remoteHost != null ? b.remoteHost : "remotehost";
                int remotePort = b.remotePort != null ? b.remotePort : 12345;
                try {
                    this.remoteAddress = new InetSocketAddress(
                            InetAddress.getByAddress(remoteHost, InetAddress.getByName(remoteAddress).getAddress()),
                            remotePort);
                } catch (UnknownHostException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override public String getProtocol() { return protocol; }
            @Override public Connector getConnector() { return connector; }
            @Override public Connection getConnection() { return connection; }
            @Override public SocketAddress getRemoteSocketAddress() { return remoteAddress; }
        }
    }
}
