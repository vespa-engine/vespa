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

    public static final List<String> ALLOWED_CIPHER_SUITES = Arrays.asList(
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_AES_128_GCM_SHA256", // TLSv1.3
            "TLS_AES_256_GCM_SHA384", // TLSv1.3
            "TLS_CHACHA20_POLY1305_SHA256"); // TLSv1.3

    public static final List<String> ALLOWED_PROTOCOLS = List.of("TLSv1.2"); // TODO Enable TLSv1.3

    private static final Logger log = Logger.getLogger(DefaultTlsContext.class.getName());

    private final SSLContext sslContext;
    private final String[] validCiphers;
    private final String[] validProtocols;

    public DefaultTlsContext(List<X509Certificate> certificates,
                             PrivateKey privateKey,
                             List<X509Certificate> caCertificates,
                             AuthorizedPeers authorizedPeers,
                             AuthorizationMode mode,
                             List<String> acceptedCiphers) {
        this(createSslContext(certificates, privateKey, caCertificates, authorizedPeers, mode),
             acceptedCiphers);
    }


    public DefaultTlsContext(SSLContext sslContext, List<String> acceptedCiphers) {
        this.sslContext = sslContext;
        this.validCiphers = getAllowedCiphers(sslContext, acceptedCiphers);
        this.validProtocols = getAllowedProtocols(sslContext);
    }


    private static String[] getAllowedCiphers(SSLContext sslContext, List<String> acceptedCiphers) {
        String[] supportedCipherSuites = sslContext.getSupportedSSLParameters().getCipherSuites();
        String[] validCipherSuites = Arrays.stream(supportedCipherSuites)
                .filter(suite -> ALLOWED_CIPHER_SUITES.contains(suite) && (acceptedCiphers.isEmpty() || acceptedCiphers.contains(suite)))
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
        newParameters.setNeedClientAuth(true);
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
