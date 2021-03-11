// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.KeyStoreType;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.security.tls.authz.PeerAuthorizerTrustManager;
import com.yahoo.security.tls.policy.AuthorizedPeers;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link TlsContext} that uses the tls configuration specified in the transport security options file.
 * The credentials are regularly reloaded to support short-lived certificates.
 *
 * @author bjorncs
 */
public class ConfigFileBasedTlsContext implements TlsContext {

    private static final Duration UPDATE_PERIOD = Duration.ofHours(1);

    private static final Logger log = Logger.getLogger(ConfigFileBasedTlsContext.class.getName());

    private final TlsContext tlsContext;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new ReloaderThreadFactory());

    public ConfigFileBasedTlsContext(Path tlsOptionsConfigFile, AuthorizationMode mode) {
        this(tlsOptionsConfigFile, mode, PeerAuthentication.NEED);
    }

    /**
     * Allows the caller to override the default peer authentication mode. This is only intended to be used in situations where
     * the TLS peer authentication is enforced at a higher protocol or application layer (e.g with {@link PeerAuthentication#WANT}).
     */
    public ConfigFileBasedTlsContext(Path tlsOptionsConfigFile, AuthorizationMode mode, PeerAuthentication peerAuthentication) {
        TransportSecurityOptions options = TransportSecurityOptions.fromJsonFile(tlsOptionsConfigFile);
        MutableX509TrustManager trustManager = new MutableX509TrustManager();
        MutableX509KeyManager keyManager = new MutableX509KeyManager();
        reloadTrustManager(options, trustManager);
        reloadKeyManager(options, keyManager);
        this.tlsContext = createDefaultTlsContext(options, mode, trustManager, keyManager, peerAuthentication);
        this.scheduler.scheduleAtFixedRate(new CryptoMaterialReloader(tlsOptionsConfigFile, scheduler, trustManager, keyManager),
                                           UPDATE_PERIOD.getSeconds()/*initial delay*/,
                                           UPDATE_PERIOD.getSeconds(),
                                           TimeUnit.SECONDS);
    }

    private static void reloadTrustManager(TransportSecurityOptions options, MutableX509TrustManager trustManager) {
        if (options.getCaCertificatesFile().isPresent()) {
            trustManager.updateTruststore(loadTruststore(options.getCaCertificatesFile().get()));
        } else {
            trustManager.useDefaultTruststore();
        }
    }

    private static void reloadKeyManager(TransportSecurityOptions options, MutableX509KeyManager keyManager) {
        if (options.getPrivateKeyFile().isPresent() && options.getCertificatesFile().isPresent()) {
            keyManager.updateKeystore(loadKeystore(options.getPrivateKeyFile().get(), options.getCertificatesFile().get()), new char[0]);
        } else {
            keyManager.useDefaultKeystore();
        }
    }

    private static KeyStore loadTruststore(Path caCertificateFile) {
        try {
            return KeyStoreBuilder.withType(KeyStoreType.PKCS12)
                    .withCertificateEntries("cert", X509CertificateUtils.certificateListFromPem(new String(Files.readAllBytes(caCertificateFile), StandardCharsets.UTF_8)))
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static KeyStore loadKeystore(Path privateKeyFile, Path certificatesFile) {
        try {
            return KeyStoreBuilder.withType(KeyStoreType.PKCS12)
                    .withKeyEntry(
                            "default",
                            KeyUtils.fromPemEncodedPrivateKey(new String(Files.readAllBytes(privateKeyFile), StandardCharsets.UTF_8)),
                            X509CertificateUtils.certificateListFromPem(new String(Files.readAllBytes(certificatesFile), StandardCharsets.UTF_8)))
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static DefaultTlsContext createDefaultTlsContext(TransportSecurityOptions options,
                                                             AuthorizationMode mode,
                                                             MutableX509TrustManager mutableTrustManager,
                                                             MutableX509KeyManager mutableKeyManager,
                                                             PeerAuthentication peerAuthentication) {

        HostnameVerification hostnameVerification = options.isHostnameValidationDisabled() ? HostnameVerification.DISABLED : HostnameVerification.ENABLED;
        PeerAuthorizerTrustManager authorizerTrustManager = options.getAuthorizedPeers()
                .map(authorizedPeers -> new PeerAuthorizerTrustManager(authorizedPeers, mode, hostnameVerification, mutableTrustManager))
                .orElseGet(() -> new PeerAuthorizerTrustManager(new AuthorizedPeers(Collections.emptySet()), AuthorizationMode.DISABLE, hostnameVerification, mutableTrustManager));
        SSLContext sslContext = new SslContextBuilder()
                .withKeyManager(mutableKeyManager)
                .withTrustManager(authorizerTrustManager)
                .build();
        List<String> acceptedCiphers = options.getAcceptedCiphers();
        Set<String> ciphers = acceptedCiphers.isEmpty() ? TlsContext.ALLOWED_CIPHER_SUITES : new HashSet<>(acceptedCiphers);
        List<String> acceptedProtocols = options.getAcceptedProtocols();
        Set<String> protocols = acceptedProtocols.isEmpty() ? TlsContext.ALLOWED_PROTOCOLS : new HashSet<>(acceptedProtocols);
        return new DefaultTlsContext(sslContext, ciphers, protocols, peerAuthentication);
    }

    // Wrapped methods from TlsContext
    @Override public SSLContext context() { return tlsContext.context(); }
    @Override public SSLParameters parameters() { return tlsContext.parameters(); }
    @Override public SSLEngine createSslEngine() { return tlsContext.createSslEngine(); }
    @Override public SSLEngine createSslEngine(String peerHost, int peerPort) { return tlsContext.createSslEngine(peerHost, peerPort); }

    @Override
    public void close() {
        try {
            scheduler.shutdownNow();
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new RuntimeException("Unable to shutdown executor before timeout");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static class CryptoMaterialReloader implements Runnable {

        final Path tlsOptionsConfigFile;
        final ScheduledExecutorService scheduler;
        final MutableX509TrustManager trustManager;
        final MutableX509KeyManager keyManager;

        CryptoMaterialReloader(Path tlsOptionsConfigFile,
                               ScheduledExecutorService scheduler,
                               MutableX509TrustManager trustManager,
                               MutableX509KeyManager keyManager) {
            this.tlsOptionsConfigFile = tlsOptionsConfigFile;
            this.scheduler = scheduler;
            this.trustManager = trustManager;
            this.keyManager = keyManager;
        }

        @Override
        public void run() {
            try {
                if (this.trustManager == null && this.keyManager == null) {
                    scheduler.shutdown();
                    return;
                }
                TransportSecurityOptions options = TransportSecurityOptions.fromJsonFile(tlsOptionsConfigFile);
                if (this.trustManager != null) {
                    reloadTrustManager(options, this.trustManager);
                }
                if (this.keyManager != null) {
                    reloadKeyManager(options, this.keyManager);
                }
            } catch (Throwable t) {
                log.log(Level.SEVERE, String.format("Failed to reload crypto material (path='%s'): %s", tlsOptionsConfigFile, t.getMessage()), t);
            }
        }
    }

    private static class ReloaderThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "tls-context-reloader");
            thread.setDaemon(true);
            return thread;
        }
    }

}
