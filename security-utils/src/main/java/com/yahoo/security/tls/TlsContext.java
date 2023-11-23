// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.yahoo.security.X509SslContext;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * A simplified version of {@link SSLContext} modelled as an interface.
 *
 * @author bjorncs
 */
public interface TlsContext extends AutoCloseable {

    /**
     * Handpicked subset of supported ciphers from https://www.openssl.org/docs/manmaster/man1/ciphers.html
     * based on Modern spec from https://wiki.mozilla.org/Security/Server_Side_TLS
     * For TLSv1.2 we only allow RSA and ECDSA with ephemeral key exchange and GCM.
     * For TLSv1.3 we allow the DEFAULT group ciphers.
     * Note that we _only_ allow AEAD ciphers for either TLS version.
     */
    Set<String> ALLOWED_CIPHER_SUITES = Set.of(
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_AES_128_GCM_SHA256", // TLSv1.3
            "TLS_AES_256_GCM_SHA384", // TLSv1.3
            "TLS_CHACHA20_POLY1305_SHA256", // TLSv1.3
            "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256"
            );

    Set<String> ALLOWED_PROTOCOLS = Set.of("TLSv1.2", "TLSv1.3");

    /** 
     * {@link SSLContext} protocol name that supports at least oldest protocol listed in {@link #ALLOWED_PROTOCOLS}
     * @see SSLContext#getInstance(String)
     */
    String SSL_CONTEXT_VERSION = "TLSv1.2";

    /**
     * @return the allowed cipher suites supported by the provided context instance
     */
    static Set<String> getAllowedCipherSuites(SSLContext context) {
        String[] supportedCiphers = context.getSupportedSSLParameters().getCipherSuites();
        Set<String> enabledCiphers = Arrays.stream(supportedCiphers)
                .filter(ALLOWED_CIPHER_SUITES::contains)
                .collect(toSet());
        if (enabledCiphers.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Non of the allowed ciphers are supported (allowed=%s, supported=%s)",
                                  ALLOWED_CIPHER_SUITES, Arrays.toString(supportedCiphers)));

        }
        return enabledCiphers;
    }

    static Set<String> getAllowedCipherSuites() { return getAllowedCipherSuites(defaultSslContext()); }

    /**
     * @return the allowed protocols supported by the provided context instance
     */
    static Set<String> getAllowedProtocols(SSLContext context) {
        String[] supportedProtocols = context.getSupportedSSLParameters().getProtocols();
        Set<String> enabledProtocols = Arrays.stream(supportedProtocols)
                .filter(ALLOWED_PROTOCOLS::contains)
                .collect(toSet());
        if (enabledProtocols.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Non of the allowed protocols are supported (allowed=%s, supported=%s)",
                                  ALLOWED_PROTOCOLS, Arrays.toString(supportedProtocols)));
        }
        return enabledProtocols;
    }

    static Set<String> getAllowedProtocols() { return getAllowedProtocols(defaultSslContext()); }

    /** @return Default {@link SSLContext} instance without certificate and using JDK's default trust store */
    static SSLContext defaultSslContext() {
        try {
            var ctx = SSLContext.getInstance(SSL_CONTEXT_VERSION);
            ctx.init(null, null, null);
            return ctx;
        } catch (NoSuchAlgorithmException e) { throw new IllegalArgumentException(e);
        } catch (KeyManagementException e) { throw new IllegalStateException(e); }
    }

    X509SslContext sslContext();
    SSLParameters parameters();

    default SSLEngine createSslEngine() {
        SSLEngine sslEngine = sslContext().context().createSSLEngine();
        sslEngine.setSSLParameters(parameters());
        return sslEngine;
    }

    default SSLEngine createSslEngine(String peerHost, int peerPort) {
        SSLEngine sslEngine = sslContext().context().createSSLEngine(peerHost, peerPort);
        sslEngine.setSSLParameters(parameters());
        return sslEngine;
    }

    default SSLSocket createClientSslSocket() throws IOException {
        var socket = (SSLSocket) sslContext().context().getSocketFactory().createSocket();
        socket.setSSLParameters(parameters());
        return socket;
    }

    default SSLServerSocket createServerSslSocket() throws IOException {
        var socket = (SSLServerSocket) sslContext().context().getServerSocketFactory().createServerSocket();
        socket.setSSLParameters(parameters());
        return socket;
    }

    @Override default void close() {}

}
