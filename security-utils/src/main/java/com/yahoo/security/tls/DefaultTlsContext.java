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
import java.util.Arrays;
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
        String[] supportedCipherSuites = sslContext.getSupportedSSLParameters().getCipherSuites();
        String[] validCipherSuites = Arrays.stream(supportedCipherSuites)
                .filter(suite -> ALLOWED_CIPHER_SUITES.contains(suite) && acceptedCiphers.contains(suite))
                .toArray(String[]::new);
        if (validCipherSuites.length == 0) {
            throw new IllegalStateException(
                    String.format("None of the allowed cipher suites are supported " +
                                          "(allowed-cipher-suites=%s, supported-cipher-suites=%s, accepted-cipher-suites=%s)",
                                  ALLOWED_CIPHER_SUITES, List.of(supportedCipherSuites), acceptedCiphers));
        }
        log.log(Level.FINE, () -> String.format("Allowed cipher suites that are supported: %s", List.of(validCipherSuites)));
        return validCipherSuites;
    }

    private static String[] getAllowedProtocols(SSLContext sslContext) {
        String[] supportedProtocols = sslContext.getSupportedSSLParameters().getProtocols();
        String[] validProtocols = Arrays.stream(supportedProtocols)
                .filter(ALLOWED_PROTOCOLS::contains)
                .toArray(String[]::new);
        if (validProtocols.length == 0) {
            throw new IllegalArgumentException(
                    String.format("None of the allowed protocols are supported (allowed-protocols=%s, supported-protocols=%s)",
                                  ALLOWED_PROTOCOLS, List.of(supportedProtocols)));
        }
        log.log(Level.FINE, () -> String.format("Allowed protocols that are supported: %s", List.of(validProtocols)));
        return validProtocols;
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
