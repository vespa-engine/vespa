// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Metric;
import org.eclipse.jetty.io.ssl.SslHandshakeListener;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author mortent
 */
public class SslHandshakeFailedListenerTest {

    private Metric metrics = mock(Metric.class);
    SslHandshakeFailedListener listener = new SslHandshakeFailedListener(metrics, "connector", 1234);

    @Test
    void includes_client_ip_dimension_present_when_peer_available() {
        listener.handshakeFailed(handshakeEvent(true), new SSLHandshakeException("Empty server certificate chain"));
        verify(metrics).createContext(eq(Map.of("serverName", "connector", "serverPort", 1234)));
    }

    @Test
    void does_not_include_client_ip_dimension_present_when_peer_unavailable() {
        listener.handshakeFailed(handshakeEvent(false), new SSLHandshakeException("Empty server certificate chain"));
        verify(metrics).createContext(eq(Map.of("serverName", "connector", "serverPort", 1234)));
    }

    private SslHandshakeListener.Event handshakeEvent(boolean includePeer) {
        var sslEngine = mock(SSLEngine.class);
        if(includePeer) when(sslEngine.getPeerHost()).thenReturn("127.0.0.1");
        return new SslHandshakeListener.Event(sslEngine);
    }
}
