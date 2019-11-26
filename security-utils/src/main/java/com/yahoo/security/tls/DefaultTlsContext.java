// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.tls.authz.PeerAuthorizerTrustManager;
import com.yahoo.security.tls.policy.AuthorizedPeers;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A static {@link TlsContext}
 *
 * @author bjorncs
 */
public class DefaultTlsContext implements TlsContext {

    private static final Logger log = Logger.getLogger(DefaultTlsContext.class.getName());

    private final SSLContext sslContext;
    private final String[] validCiphers;
    private final String[] validProtocols;
    private final PeerAuthentication peerAuthentication;

    public DefaultTlsContext(List<X509Certificate> certificates,
                             PrivateKey privateKey,
                             List<X509Certificate> caCertificates,
                             AuthorizedPeers authorizedPeers,
                             AuthorizationMode mode,
                             PeerAuthentication peerAuthentication) {
        this(createSslContext(certificates, privateKey, caCertificates, authorizedPeers, mode), peerAuthentication);
    }

    public DefaultTlsContext(SSLContext sslContext, PeerAuthentication peerAuthentication) {
        this(sslContext, TlsContext.ALLOWED_CIPHER_SUITES, peerAuthentication);
    }

    public DefaultTlsContext(SSLContext sslContext) {
        this(sslContext, TlsContext.ALLOWED_CIPHER_SUITES, PeerAuthentication.NEED);
    }

    DefaultTlsContext(SSLContext sslContext, Set<String> acceptedCiphers, PeerAuthentication peerAuthentication) {
        this.sslContext = sslContext;
        this.peerAuthentication = peerAuthentication;
        this.validCiphers = getAllowedCiphers(sslContext, acceptedCiphers);
        this.validProtocols = getAllowedProtocols(sslContext);
    }

    private static String[] getAllowedCiphers(SSLContext sslContext, Set<String> acceptedCiphers) {
        Set<String> supportedCiphers = TlsContext.getAllowedCipherSuites(sslContext);
        String[] allowedCiphers = supportedCiphers.stream()
                .filter(acceptedCiphers::contains)
                .toArray(String[]::new);
        if (allowedCiphers.length == 0) {
            throw new IllegalStateException(
                    String.format("None of the accepted ciphers are supported (supported=%s, accepted=%s)",
                                  supportedCiphers, acceptedCiphers));
        }
        log.log(Level.FINE, () -> String.format("Allowed cipher suites that are supported: %s", List.of(allowedCiphers)));
        return allowedCiphers;
    }

    private static String[] getAllowedProtocols(SSLContext sslContext) {
        Set<String> allowedProtocols = TlsContext.getAllowedProtocols(sslContext);
        log.log(Level.FINE, () -> String.format("Allowed protocols that are supported: %s", List.of(allowedProtocols)));
        return allowedProtocols.toArray(String[]::new);
    }

    @Override
    public SSLContext context() {
        return sslContext;
    }

    @Override
    public SSLParameters parameters() {
        return createSslParameters();
    }

    @Override
    public SSLEngine createSslEngine() {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setSSLParameters(createSslParameters());
        return sslEngine;
    }

    @Override
    public SSLEngine createSslEngine(String peerHost, int peerPort) {
        SSLEngine sslEngine = sslContext.createSSLEngine(peerHost, peerPort);
        sslEngine.setSSLParameters(createSslParameters());
        return sslEngine;
    }

    private SSLParameters createSslParameters() {
        SSLParameters newParameters = sslContext.getDefaultSSLParameters();
        newParameters.setCipherSuites(validCiphers);
        newParameters.setProtocols(validProtocols);
        switch (peerAuthentication) {
            case WANT:
                newParameters.setWantClientAuth(true);
                break;
            case NEED:
                newParameters.setNeedClientAuth(true);
                break;
            case DISABLED:
                break;
            default:
                throw new UnsupportedOperationException("Unknown peer authentication: " + peerAuthentication);
        }
        return newParameters;
    }

    private static SSLContext createSslContext(List<X509Certificate> certificates,
                                               PrivateKey privateKey,
                                               List<X509Certificate> caCertificates,
                                               AuthorizedPeers authorizedPeers,
                                               AuthorizationMode mode) {
        SslContextBuilder builder = new SslContextBuilder();
        if (!certificates.isEmpty()) {
            builder.withKeyStore(privateKey, certificates);
        }
        if (!caCertificates.isEmpty()) {
            builder.withTrustStore(caCertificates);
        }
        if (authorizedPeers != null) {
            builder.withTrustManagerFactory(truststore -> new PeerAuthorizerTrustManager(authorizedPeers, mode, truststore));
        } else {
            builder.withTrustManagerFactory(truststore -> new PeerAuthorizerTrustManager(new AuthorizedPeers(Set.of()), AuthorizationMode.DISABLE, truststore));
        }
        return builder.build();
    }


}
