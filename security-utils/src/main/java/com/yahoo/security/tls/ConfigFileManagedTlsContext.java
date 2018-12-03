// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.tls.authz.PeerAuthorizerTrustManager;
import com.yahoo.security.tls.authz.PeerAuthorizerTrustManagersFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link TlsContext} that regularly reloads the credentials referred to from the transport security options file.
 *
 * @author bjorncs
 */
public class ConfigFileManagedTlsContext implements TlsContext {

    private static final Duration UPDATE_PERIOD = Duration.ofHours(1);
    private static final List<String> ALLOWED_CIPHER_SUITS = Arrays.asList(
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256");

    private static final Logger log = Logger.getLogger(ConfigFileManagedTlsContext.class.getName());

    private final Path tlsOptionsConfigFile;
    private final PeerAuthorizerTrustManager.Mode mode;
    private final AtomicReference<SSLContext> currentSslContext;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "tls-context-reloader");
                thread.setDaemon(true);
                return thread;
            });


    public ConfigFileManagedTlsContext(Path tlsOptionsConfigFile, PeerAuthorizerTrustManager.Mode mode) {
        this.tlsOptionsConfigFile = tlsOptionsConfigFile;
        this.mode = mode;
        this.currentSslContext = new AtomicReference<>(createSslContext(tlsOptionsConfigFile, mode));
        this.scheduler.scheduleAtFixedRate(new SslContextReloader(),
                                           UPDATE_PERIOD.getSeconds()/*initial delay*/,
                                           UPDATE_PERIOD.getSeconds(),
                                           TimeUnit.SECONDS);
    }

    public SSLEngine createSslEngine() {
        SSLEngine sslEngine = currentSslContext.get().createSSLEngine();
        restrictSetOfEnabledCiphers(sslEngine);
        return sslEngine;
    }

    private static void restrictSetOfEnabledCiphers(SSLEngine sslEngine) {
        String[] validCipherSuits = Arrays.stream(sslEngine.getSupportedCipherSuites())
                .filter(ALLOWED_CIPHER_SUITS::contains)
                .toArray(String[]::new);
        if (validCipherSuits.length == 0) {
            throw new IllegalStateException("None of the allowed cipher suits are supported");
        }
        log.log(Level.FINE, () -> String.format("Allowed cipher suits that are supported: %s", Arrays.toString(validCipherSuits)));
        sslEngine.setEnabledCipherSuites(validCipherSuits);
    }

    private static SSLContext createSslContext(Path tlsOptionsConfigFile, PeerAuthorizerTrustManager.Mode mode) {
        TransportSecurityOptions options = TransportSecurityOptions.fromJsonFile(tlsOptionsConfigFile);
        SslContextBuilder builder = new SslContextBuilder();
        options.getCertificatesFile()
                .ifPresent(certificates -> builder.withKeyStore(options.getPrivateKeyFile().get(), certificates));
        options.getCaCertificatesFile().ifPresent(builder::withTrustStore);
        options.getAuthorizedPeers().ifPresent(
                authorizedPeers -> builder.withTrustManagerFactory(new PeerAuthorizerTrustManagersFactory(authorizedPeers, mode)));
        return builder.build();
    }

    @Override
    public void close() {
        try {
            scheduler.shutdownNow();
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private class SslContextReloader implements Runnable {
        @Override
        public void run() {
            try {
                currentSslContext.set(createSslContext(tlsOptionsConfigFile, mode));
            } catch (Throwable t) {
                log.log(Level.SEVERE, String.format("Failed to load SSLContext (path='%s'): %s", tlsOptionsConfigFile, t.getMessage()), t);
            }
        }
    }

}
