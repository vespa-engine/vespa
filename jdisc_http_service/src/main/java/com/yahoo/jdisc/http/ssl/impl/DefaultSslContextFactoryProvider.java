// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.impl;

import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.http.ssl.SslContextFactoryProvider;
import com.yahoo.security.tls.ReloadingTlsContext;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.TransportSecurityUtils;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * The default implementation of {@link SslContextFactoryProvider} to be injected into connectors without explicit ssl configuration
 *
 * @author bjorncs
 */
public class DefaultSslContextFactoryProvider extends AbstractComponent implements SslContextFactoryProvider {

    private final TlsContext tlsContext = TransportSecurityUtils.getConfigFile()
            .map(configFile -> new ReloadingTlsContext(configFile, TransportSecurityUtils.getInsecureAuthorizationMode()))
            .orElse(null);

    @Override
    public SslContextFactory getInstance(String containerId, int port) {
        if (tlsContext != null) {
            return new TlsContextManagedSslContextFactory(tlsContext);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void deconstruct() {
        if (tlsContext != null) tlsContext.close();
    }
}