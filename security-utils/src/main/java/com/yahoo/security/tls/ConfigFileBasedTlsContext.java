// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.KeyStoreType;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.MutableX509KeyManager;
import com.yahoo.security.MutableX509TrustManager;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.security.X509SslContext;

import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.security.tls.HostnameVerification.DISABLED;
import static com.yahoo.security.tls.HostnameVerification.ENABLED;

/**
 * A {@link TlsContext} that uses the tls configuration specified in the transport security options file.
 * The credentials and authorization rules are regularly reloaded to support short-lived certificates and policy changes.
 *
 * @author bjorncs
 */
public class ConfigFileBasedTlsContext implements TlsContext {

    private static final Duration UPDATE_PERIOD = Duration.ofHours(1);

    private static final Logger log = Logger.getLogger(ConfigFileBasedTlsContext.class.getName());

    private final TlsContext tlsContext;
    private final CryptoMaterialReloader reloader;
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
        var hostnameVerification = options.isHostnameValidationDisabled() ? DISABLED : ENABLED;
        var authorizedPeers = options.getAuthorizedPeers();
        var authorizerTrustManager = new PeerAuthorizerTrustManager(authorizedPeers, mode, hostnameVerification, trustManager);
        this.tlsContext = createDefaultTlsContext(options, authorizerTrustManager, keyManager, peerAuthentication);
        this.reloader = new CryptoMaterialReloader(tlsOptionsConfigFile, trustManager, keyManager,
                                                   authorizerTrustManager, authorizedPeers);
        this.scheduler.scheduleAtFixedRate(reloader,
                                           UPDATE_PERIOD.getSeconds() /*initial delay*/,
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
                                                             PeerAuthorizerTrustManager authorizerTrustManager,
                                                             MutableX509KeyManager mutableKeyManager,
                                                             PeerAuthentication peerAuthentication) {
        var sslContext = new SslContextBuilder()
                .withKeyManager(mutableKeyManager)
                .withTrustManager(authorizerTrustManager)
                .buildContext();
        List<String> acceptedCiphers = options.getAcceptedCiphers();
        Set<String> ciphers = acceptedCiphers.isEmpty() ? TlsContext.ALLOWED_CIPHER_SUITES : new HashSet<>(acceptedCiphers);
        List<String> acceptedProtocols = options.getAcceptedProtocols();
        Set<String> protocols = acceptedProtocols.isEmpty() ? TlsContext.ALLOWED_PROTOCOLS : new HashSet<>(acceptedProtocols);
        return DefaultTlsContext.of(sslContext, ciphers, protocols, peerAuthentication);
    }

    // Wrapped methods from TlsContext
    @Override public X509SslContext sslContext() { return tlsContext.sslContext(); }
    @Override public SSLParameters parameters() { return tlsContext.parameters(); }

    /** Reloads credentials and authorization rules immediately. Intended for testing. */
    void reload() { reloader.run(); }

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
        final MutableX509TrustManager trustManager;
        final MutableX509KeyManager keyManager;
        final PeerAuthorizerTrustManager authorizerTrustManager;
        AuthorizedPeers currentAuthorizedPeers;

        CryptoMaterialReloader(Path tlsOptionsConfigFile,
                               MutableX509TrustManager trustManager,
                               MutableX509KeyManager keyManager,
                               PeerAuthorizerTrustManager authorizerTrustManager,
                               AuthorizedPeers currentAuthorizedPeers) {
            this.tlsOptionsConfigFile = tlsOptionsConfigFile;
            this.trustManager = trustManager;
            this.keyManager = keyManager;
            this.authorizerTrustManager = authorizerTrustManager;
            this.currentAuthorizedPeers = currentAuthorizedPeers;
        }

        @Override
        public synchronized void run() {
            try {
                TransportSecurityOptions options = TransportSecurityOptions.fromJsonFile(tlsOptionsConfigFile);
                reloadTrustManager(options, trustManager);
                reloadKeyManager(options, keyManager);
                AuthorizedPeers authorizedPeers = options.getAuthorizedPeers();
                if ( ! authorizedPeers.equals(currentAuthorizedPeers)) {
                    authorizerTrustManager.updateAuthorizedPeers(authorizedPeers);
                    currentAuthorizedPeers = authorizedPeers;
                    log.log(Level.INFO, "Reloaded authorized peers from " + tlsOptionsConfigFile);
                }
            } catch (Throwable t) {
                log.log(Level.SEVERE, String.format(Locale.ROOT, "Failed to reload crypto material (path='%s'): %s", tlsOptionsConfigFile, t.getMessage()), t);
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
