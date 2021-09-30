// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.impl;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ssl.SslContextFactoryProvider;
import com.yahoo.security.tls.ConfigFileBasedTlsContext;
import com.yahoo.security.tls.PeerAuthentication;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.TransportSecurityUtils;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.nio.file.Path;

/**
 * The default implementation of {@link SslContextFactoryProvider} to be injected into connectors without explicit ssl configuration.
 *
 * @author bjorncs
 */
public class DefaultSslContextFactoryProvider extends AbstractComponent implements SslContextFactoryProvider {

    private final SslContextFactoryProvider instance;

    @Inject
    public DefaultSslContextFactoryProvider(ConnectorConfig connectorConfig) {
        this.instance = TransportSecurityUtils.getConfigFile()
                .map(configFile -> createTlsContextBasedProvider(connectorConfig, configFile))
                .orElseGet(ThrowingSslContextFactoryProvider::new);
    }

    private static SslContextFactoryProvider createTlsContextBasedProvider(ConnectorConfig connectorConfig, Path configFile) {
        return new StaticTlsContextBasedProvider(
                new ConfigFileBasedTlsContext(
                        configFile, TransportSecurityUtils.getInsecureAuthorizationMode(), getPeerAuthenticationMode(connectorConfig)));
    }

    /**
     * Allows white-listing of user provided uri paths.
     * JDisc will delegate the enforcement of peer authentication from the TLS to the HTTP layer if {@link ConnectorConfig.TlsClientAuthEnforcer#enable()} is true.
     */
    private static PeerAuthentication getPeerAuthenticationMode(ConnectorConfig connectorConfig) {
        return connectorConfig.tlsClientAuthEnforcer().enable()
                ? PeerAuthentication.WANT
                : PeerAuthentication.NEED;
    }

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

        @Override public void deconstruct() { tlsContext.close(); }
    }
}