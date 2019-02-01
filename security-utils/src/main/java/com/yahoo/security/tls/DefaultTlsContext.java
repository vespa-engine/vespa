// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.tls.authz.PeerAuthorizerTrustManagersFactory;
import com.yahoo.security.tls.policy.AuthorizedPeers;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
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
    private final List<String> acceptedCiphers;

    public DefaultTlsContext(List<X509Certificate> certificates,
                             PrivateKey privateKey,
                             List<X509Certificate> caCertificates,
                             AuthorizedPeers authorizedPeers,
                             AuthorizationMode mode,
                             List<String> acceptedCiphers) {
        this.sslContext = createSslContext(certificates, privateKey, caCertificates, authorizedPeers, mode);
        this.acceptedCiphers = acceptedCiphers;
    }

    public DefaultTlsContext(Path tlsOptionsConfigFile, AuthorizationMode mode) {
        TransportSecurityOptions options = TransportSecurityOptions.fromJsonFile(tlsOptionsConfigFile);
        this.sslContext = createSslContext(options, mode);
        this.acceptedCiphers = options.getAcceptedCiphers();
    }

    @Override
    public SSLEngine createSslEngine() {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        restrictSetOfEnabledCiphers(sslEngine, acceptedCiphers);
        restrictTlsProtocols(sslEngine);
        return sslEngine;
    }

    private static void restrictSetOfEnabledCiphers(SSLEngine sslEngine, List<String> acceptedCiphers) {
        String[] validCipherSuites = Arrays.stream(sslEngine.getSupportedCipherSuites())
                .filter(suite -> ALLOWED_CIPHER_SUITES.contains(suite) && (acceptedCiphers.isEmpty() || acceptedCiphers.contains(suite)))
                .toArray(String[]::new);
        if (validCipherSuites.length == 0) {
            throw new IllegalStateException(
                    String.format("None of the allowed cipher suites are supported " +
                                          "(allowed-cipher-suites=%s, supported-cipher-suites=%s, accepted-cipher-suites=%s)",
                                  ALLOWED_CIPHER_SUITES, List.of(sslEngine.getSupportedCipherSuites()), acceptedCiphers));
        }
        log.log(Level.FINE, () -> String.format("Allowed cipher suites that are supported: %s", Arrays.toString(validCipherSuites)));
        sslEngine.setEnabledCipherSuites(validCipherSuites);
    }

    private static void restrictTlsProtocols(SSLEngine sslEngine) {
        String[] validProtocols = Arrays.stream(sslEngine.getSupportedProtocols())
                .filter(ALLOWED_PROTOCOLS::contains)
                .toArray(String[]::new);
        if (validProtocols.length == 0) {
            throw new IllegalArgumentException(
                    String.format("Non of the allowed protocols are supported (allowed-protocols=%s, supported-protocols=%s)",
                                  ALLOWED_PROTOCOLS, Arrays.toString(sslEngine.getSupportedProtocols())));
        }
        log.log(Level.FINE, () -> String.format("Allowed protocols that are supported: %s", Arrays.toString(validProtocols)));
        sslEngine.setEnabledProtocols(validProtocols);
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
            builder.withTrustManagerFactory(new PeerAuthorizerTrustManagersFactory(authorizedPeers, mode));
        }
        return builder.build();
    }

    private static SSLContext createSslContext(TransportSecurityOptions options, AuthorizationMode mode) {
        SslContextBuilder builder = new SslContextBuilder();
        options.getCertificatesFile()
                .ifPresent(certificates -> builder.withKeyStore(options.getPrivateKeyFile().get(), certificates));
        options.getCaCertificatesFile().ifPresent(builder::withTrustStore);
        if (mode != AuthorizationMode.DISABLE) {
            options.getAuthorizedPeers().ifPresent(
                    authorizedPeers -> builder.withTrustManagerFactory(new PeerAuthorizerTrustManagersFactory(authorizedPeers, mode)));
        }
        return builder.build();
    }

}
