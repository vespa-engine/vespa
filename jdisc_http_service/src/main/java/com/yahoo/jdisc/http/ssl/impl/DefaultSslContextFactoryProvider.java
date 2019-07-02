// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.impl;

import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.http.ssl.SslContextFactoryProvider;
import com.yahoo.security.tls.ConfigFiledBasedTlsContext;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.TransportSecurityUtils;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * The default implementation of {@link SslContextFactoryProvider} to be injected into connectors without explicit ssl configuration
 *
 * @author bjorncs
 */
public class DefaultSslContextFactoryProvider extends AbstractComponent implements SslContextFactoryProvider {

    private final SslContextFactoryProvider instance = TransportSecurityUtils.getConfigFile()
            .map(configFile -> (SslContextFactoryProvider) new StaticTlsContextBasedProvider(
                    new ConfigFiledBasedTlsContext(configFile, TransportSecurityUtils.getInsecureAuthorizationMode())))
            .orElseGet(ThrowingSslContextFactoryProvider::new);

    @Override
    public SslContextFactory getInstance(String containerId, int port) {
        return instance.getInstance(containerId, port);
    }

    @Override
    public void deconstruct() {
        instance.close();
    }

    private static class ThrowingSslContextFactoryProvider implements SslContextFactoryProvider {
        @Override
        public SslContextFactory getInstance(String containerId, int port) {
            throw new UnsupportedOperationException();
        }
    }

    private static class StaticTlsContextBasedProvider extends TlsContextBasedProvider {
        final TlsContext tlsContext;

        StaticTlsContextBasedProvider(TlsContext tlsContext) {
            this.tlsContext = tlsContext;
        }

        @Override
        protected TlsContext getTlsContext(String containerId, int port) {
            return tlsContext;
        }
    }
}