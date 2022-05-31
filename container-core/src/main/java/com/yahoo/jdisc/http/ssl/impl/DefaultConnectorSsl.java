// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.impl;

import com.yahoo.jdisc.http.SslProvider;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.SSLContext;
import java.security.KeyStore;
import java.util.List;

/**
 * Default implementation of {@link SslProvider} backed by {@link SslContextFactory.Server}
 *
 * @author bjorncs
 */
public class DefaultConnectorSsl implements SslProvider.ConnectorSsl {

    private SSLContext sslContext;
    private ClientAuth clientAuth;
    private List<String> cipherSuites = List.of();
    private List<String> protocolVersions = List.of();
    private KeyStore keystore;
    private char[] keystorePassword;
    private KeyStore truststore;
    private char[] truststorePassword;

    @Override
    public SslProvider.ConnectorSsl setSslContext(SSLContext ctx) {
        this.sslContext = ctx; return this;
    }

    @Override
    public SslProvider.ConnectorSsl setClientAuth(SslProvider.ConnectorSsl.ClientAuth auth) {
        this.clientAuth = auth; return this;
    }

    @Override
    public SslProvider.ConnectorSsl setEnabledCipherSuites(List<String> ciphers) {
        this.cipherSuites = ciphers; return this;
    }

    @Override
    public SslProvider.ConnectorSsl setEnabledProtocolVersions(List<String> versions) {
        this.protocolVersions = versions; return this;
    }

    @Override
    public SslProvider.ConnectorSsl setKeystore(KeyStore keystore, char[] password) {
        this.keystore = keystore; this.keystorePassword = password; return this;
    }

    @Override
    public SslProvider.ConnectorSsl setKeystore(KeyStore keystore) {
        this.keystore = keystore; return this;
    }

    @Override
    public SslProvider.ConnectorSsl setTruststore(KeyStore truststore, char[] password) {
        this.truststore = truststore; this.truststorePassword = password; return this;
    }

    @Override
    public SslProvider.ConnectorSsl setTruststore(KeyStore truststore) {
        this.truststore = truststore; return this;
    }

    public SslContextFactory.Server createSslContextFactory() {
        SslContextFactory.Server ssl = new SslContextFactory.Server();
        if (sslContext != null) ssl.setSslContext(sslContext);
        if (keystore != null) ssl.setKeyStore(keystore);
        if (keystorePassword != null) ssl.setKeyStorePassword(new String(keystorePassword));
        if (truststore != null) ssl.setTrustStore(truststore);
        if (truststorePassword != null) ssl.setTrustStorePassword(new String(truststorePassword));
        switch (clientAuth) {
            case DISABLED:
                ssl.setWantClientAuth(false);
                ssl.setNeedClientAuth(false);
                break;
            case NEED:
                ssl.setWantClientAuth(false);
                ssl.setNeedClientAuth(true);
                break;
            case WANT:
                ssl.setWantClientAuth(true);
                ssl.setNeedClientAuth(false);
                break;
            default:
                throw new IllegalArgumentException(clientAuth.name());
        }
        if (!cipherSuites.isEmpty()) SslContextFactoryUtils.setEnabledCipherSuites(ssl, sslContext, cipherSuites);
        if (!protocolVersions.isEmpty()) SslContextFactoryUtils.setEnabledProtocols(ssl, sslContext, protocolVersions);
        return ssl;
    }
}
