// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:charlesk@yahoo-inc.com">Charles Kim</a>
 */
public class SslContextFactory {

    private static final Logger log = Logger.getLogger(SslContextFactory.class.getName());
    private static final String DEFAULT_ALGORITHM = "SunX509";
    private static final String DEFAULT_PROTOCOL = "TLS";
    private final SSLContext sslContext;

    private SslContextFactory(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public SSLContext getServerSSLContext() {
        return this.sslContext;
    }

    public static SslContextFactory newInstanceFromTrustStore(SslKeyStore trustStore) {
        return newInstance(DEFAULT_ALGORITHM, DEFAULT_PROTOCOL, null, trustStore);
    }

    public static SslContextFactory newInstance(SslKeyStore trustStore, SslKeyStore keyStore) {
        return newInstance(DEFAULT_ALGORITHM, DEFAULT_PROTOCOL, keyStore, trustStore);
    }

    public static SslContextFactory newInstance(String sslAlgorithm, String sslProtocol,
                                                SslKeyStore keyStore, SslKeyStore trustStore) {
        log.fine("Configuring SSLContext...");
        log.fine("Using " + sslAlgorithm + " algorithm.");
        try {
            SSLContext sslContext = SSLContext.getInstance(sslProtocol);
            sslContext.init(
                    keyStore == null ? null : getKeyManagers(keyStore, sslAlgorithm),
                    trustStore == null ? null : getTrustManagers(trustStore, sslAlgorithm),
                    null);
            return new SslContextFactory(sslContext);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Got exception creating SSLContext.", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Used for the key store, which contains the SSL cert and private key.
     */
    public static javax.net.ssl.KeyManager[] getKeyManagers(SslKeyStore keyStore,
                                                            String sslAlgorithm)
            throws NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException,
                   KeyStoreException {

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(sslAlgorithm);
        keyManagerFactory.init(
                keyStore.loadJavaKeyStore(),
                keyStore.getKeyStorePassword().map(String::toCharArray).orElse(null));
        log.fine("KeyManagerFactory initialized with keystore");
        return keyManagerFactory.getKeyManagers();
    }

    /**
     * Used for the trust store, which contains certificates from other parties that you expect to communicate with,
     * or from Certificate Authorities that you trust to identify other parties.
     */
    public static javax.net.ssl.TrustManager[] getTrustManagers(SslKeyStore trustStore,
                                                                String sslAlgorithm)
            throws NoSuchAlgorithmException, CertificateException, IOException, KeyStoreException {

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(sslAlgorithm);
        trustManagerFactory.init(trustStore.loadJavaKeyStore());
        log.fine("TrustManagerFactory initialized with truststore.");
        return trustManagerFactory.getTrustManagers();
    }

}
