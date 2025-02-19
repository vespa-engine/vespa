// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.http.ConnectorConfig;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.HttpInput;
import org.eclipse.jetty.server.Request;
import org.mockito.stubbing.Answer;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    public Request build() {
        int localPort = this.localPort != null ? this.localPort : 8080;
        String scheme = this.uriScheme != null ? this.uriScheme : "http";
        String serverName = this.uriServerName != null ? this.uriServerName : "localhost";
        int uriPort = this.uriPort != null ? this.uriPort : 8080;
        String path = this.uriPath;
        String query = this.uriQuery;
        String remoteAddress = this.remoteAddress != null ? this.remoteAddress : "1.2.3.4";
        String remoteHost = this.remoteHost != null ? this.remoteHost : "remotehost";
        Integer remotePort = this.remotePort != null ? this.remotePort : 12345;

        HttpChannel channel = mock(HttpChannel.class);
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
        when(connection.getHttpChannel()).thenReturn(channel);
        when(channel.getConnector()).thenReturn(connector);
        when(channel.getConnection()).thenReturn(connection);

        HttpInput httpInput = mock(HttpInput.class);
        when(httpInput.getContentReceived()).thenReturn(2345L);

        Request request = mock(Request.class);
        when(request.getHttpChannel()).thenReturn(channel);
        when(request.getHttpInput()).thenReturn(httpInput);
        when(request.getProtocol()).thenReturn("HTTP/1.1");
        when(request.getScheme()).thenReturn(scheme);
        when(request.getServerName()).thenReturn(serverName);
        when(request.getRemoteAddr()).thenReturn(remoteAddress);
        when(request.getRemotePort()).thenReturn(remotePort);
        when(request.getRemoteHost()).thenReturn(remoteHost);
        when(request.getLocalPort()).thenReturn(uriPort);
        when(request.getMethod()).thenReturn("GET");
        when(request.getQueryString()).thenReturn(query);
        when(request.getRequestURI()).thenReturn(path);

        mockCharacterEncodingHandling(request);
        mockHeaderHandling(request);
        mockParameterHandling(request);
        mockAttributeHandling(request);

        return request;
    }

    private void mockCharacterEncodingHandling(Request request) {
        try {
            AtomicReference<String> characterEncoding = new AtomicReference<>("");
            when(request.getCharacterEncoding()).thenAnswer((Answer<String>) ignored -> characterEncoding.get());
            doAnswer((Answer<Void>) invocation -> {
                String value = invocation.getArgument(0);
                characterEncoding.set(value);
                return null;
            }).when(request).setCharacterEncoding(anyString());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private void mockHeaderHandling(Request request) {
        Map<String, List<String>> headers = new ConcurrentHashMap<>(this.headers);
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(headers.keySet()));
        when(request.getHeaders(anyString())).thenAnswer((Answer<Enumeration<String>>) invocation -> {
            String key = invocation.getArgument(0);
            List<String> values = headers.get(key);
            return values != null ? Collections.enumeration(values) : Collections.enumeration(List.of());
        });
        when(request.getHeader(anyString())).thenAnswer((Answer<String>) invocation -> {
            String name = invocation.getArgument(0);
            List<String> values = headers.get(name);
            if (values == null || values.isEmpty()) return null;
            return values.get(0);
        });
    }

    private void mockParameterHandling(Request request) {
        Map<String, String[]> parameters = new ConcurrentHashMap<>();
        this.parameters.forEach((key, values) -> parameters.put(key, values.toArray(String[]::new)));
        when(request.getParameterMap()).thenReturn(parameters);
    }

    private void mockAttributeHandling(Request request) {
        Map<String, Object> attributes = new ConcurrentHashMap<>(this.attributes);

        when(request.getAttribute(any())).thenAnswer(invocation -> {
            String attributeName = invocation.getArgument(0);
            return attributes.get(attributeName);
        });
        doAnswer((Answer<Void>) invocation -> {
            String attributeName = invocation.getArgument(0);
            Object attributeValue = invocation.getArgument(1);
            attributes.put(attributeName, attributeValue);
            return null;
        }).when(request).setAttribute(anyString(), any());
        doAnswer((Answer<Void>) invocation -> {
            String attributeName = invocation.getArgument(0);
            attributes.remove(attributeName);
            return null;
        }).when(request).removeAttribute(anyString());
    }
}
