// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
     *
     * TODO(bjorncs) Add new ciphers once migrated to JDK-17 (also available in 11.0.13):
     * - TLS_CHACHA20_POLY1305_SHA256, TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256, TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256
     */
    Set<String> ALLOWED_CIPHER_SUITES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_AES_128_GCM_SHA256", // TLSv1.3
            "TLS_AES_256_GCM_SHA384" // TLSv1.3
            )));

    // TODO Enable TLSv1.3 after upgrading to JDK 17
    Set<String> ALLOWED_PROTOCOLS = Collections.singleton("TLSv1.2");
    String SSL_CONTEXT_VERSION = "TLS"; // Use SSLContext implementations that supports all TLS versions

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

    SSLContext context();

    SSLParameters parameters();

    SSLEngine createSslEngine();

    SSLEngine createSslEngine(String peerHost, int peerPort);

    @Override default void close() {}

}
