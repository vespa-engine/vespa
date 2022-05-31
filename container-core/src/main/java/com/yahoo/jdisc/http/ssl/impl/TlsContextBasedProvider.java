// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.impl;

import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.http.SslProvider;
import com.yahoo.security.tls.TlsContext;

import javax.net.ssl.SSLParameters;
import java.util.List;

/**
 * A {@link SslProvider} that configures SSL from {@link TlsContext} instances.
 *
 * @author bjorncs
 */
public abstract class TlsContextBasedProvider extends AbstractComponent implements SslProvider {

    protected abstract TlsContext getTlsContext(String containerId, int port);

    @Override
    public void configureSsl(ConnectorSsl ssl, String name, int port) {
        TlsContext tlsContext = getTlsContext(name, port);
        SSLParameters parameters = tlsContext.parameters();
        ssl.setSslContext(tlsContext.context());
        ssl.setEnabledProtocolVersions(List.of(parameters.getProtocols()));
        ssl.setEnabledCipherSuites(List.of(parameters.getCipherSuites()));
        if (parameters.getNeedClientAuth()) {
            ssl.setClientAuth(ConnectorSsl.ClientAuth.NEED);
        } else if (parameters.getWantClientAuth()) {
            ssl.setClientAuth(ConnectorSsl.ClientAuth.WANT);
        } else {
            ssl.setClientAuth(ConnectorSsl.ClientAuth.DISABLED);
        }
    }
}
