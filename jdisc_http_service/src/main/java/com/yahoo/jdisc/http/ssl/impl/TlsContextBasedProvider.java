// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.impl;

import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.http.ssl.SslContextFactoryProvider;
import com.yahoo.security.tls.TlsContext;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.util.Arrays;

/**
 * A {@link SslContextFactoryProvider} that creates {@link SslContextFactory} instances from {@link TlsContext} instances.
 *
 * @author bjorncs
 */
public abstract class TlsContextBasedProvider extends AbstractComponent implements SslContextFactoryProvider {

    protected abstract TlsContext getTlsContext(String containerId, int port);

    @Override
    public final SslContextFactory getInstance(String containerId, int port) {
        TlsContext tlsContext = getTlsContext(containerId, port);
        SSLContext sslContext = tlsContext.context();
        SSLParameters parameters = tlsContext.parameters();

        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setSslContext(sslContext);

        sslContextFactory.setNeedClientAuth(parameters.getNeedClientAuth());
        sslContextFactory.setWantClientAuth(parameters.getWantClientAuth());

        String[] enabledProtocols = parameters.getProtocols();
        sslContextFactory.setIncludeProtocols(enabledProtocols);
        String[] supportedProtocols = sslContext.getSupportedSSLParameters().getProtocols();
        sslContextFactory.setExcludeProtocols(createExclusionList(enabledProtocols, supportedProtocols));

        String[] enabledCiphers = parameters.getCipherSuites();
        String[] supportedCiphers = sslContext.getSupportedSSLParameters().getCipherSuites();
        sslContextFactory.setIncludeCipherSuites(enabledCiphers);
        sslContextFactory.setExcludeCipherSuites(createExclusionList(enabledCiphers, supportedCiphers));
        return sslContextFactory;
    }

    private static String[] createExclusionList(String[] enabledValues, String[] supportedValues) {
        return Arrays.stream(supportedValues)
                .filter(supportedValue ->
                                Arrays.stream(enabledValues)
                                        .noneMatch(enabledValue -> enabledValue.equals(supportedValue)))
                .toArray(String[]::new);
    }

}
