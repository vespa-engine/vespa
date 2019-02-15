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
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link TlsContext} that regularly reloads the credentials referred to from the transport security options file.
 *
 * @author bjorncs
 */
public class ReloadingTlsContext implements TlsContext {

    private static final Duration UPDATE_PERIOD = Duration.ofHours(1);

    private static final Logger log = Logger.getLogger(ReloadingTlsContext.class.getName());

    private final Path tlsOptionsConfigFile;
    private final TlsContext tlsContext;
    private final MutableX509TrustManager trustManager = new MutableX509TrustManager();
    private final MutableX509KeyManager keyManager = new MutableX509KeyManager();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "tls-context-reloader");
                thread.setDaemon(true);
                return thread;
            });

    public ReloadingTlsContext(Path tlsOptionsConfigFile, AuthorizationMode mode) {
        this.tlsOptionsConfigFile = tlsOptionsConfigFile;
        TransportSecurityOptions options = TransportSecurityOptions.fromJsonFile(tlsOptionsConfigFile);
        reloadCryptoMaterial(options, trustManager, keyManager);
        this.tlsContext = createDefaultTlsContext(options, mode, trustManager, keyManager);
        this.scheduler.scheduleAtFixedRate(new CryptoMaterialReloader(),
                                           UPDATE_PERIOD.getSeconds()/*initial delay*/,
                                           UPDATE_PERIOD.getSeconds(),
                                           TimeUnit.SECONDS);
    }

    private static void reloadCryptoMaterial(TransportSecurityOptions options,
                                             MutableX509TrustManager trustManager,
                                             MutableX509KeyManager keyManager) {
        if (options.getCaCertificatesFile().isPresent()) {
            trustManager.updateTruststore(loadTruststore(options.getCaCertificatesFile().get()));
        } else {
            trustManager.useDefaultTruststore();
        }

        if (options.getPrivateKeyFile().isPresent() && options.getCertificatesFile().isPresent()) {
            keyManager.updateKeystore(loadKeystore(options.getPrivateKeyFile().get(), options.getCertificatesFile().get()), new char[0]);
        } else {
            keyManager.useDefaultKeystore();
        }
    }

    private static KeyStore loadTruststore(Path caCertificateFile) {
        try {
            List<X509Certificate> caCertificates = X509CertificateUtils.certificateListFromPem(Files.readString(caCertificateFile));
            KeyStoreBuilder trustStoreBuilder = KeyStoreBuilder.withType(KeyStoreType.PKCS12);
            for (int i = 0; i < caCertificates.size(); i++) {
                trustStoreBuilder.withCertificateEntry("cert-" + i, caCertificates.get(i));
            }
            return trustStoreBuilder.build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static KeyStore loadKeystore(Path privateKeyFile, Path certificatesFile) {
        try {
            return KeyStoreBuilder.withType(KeyStoreType.PKCS12)
                    .withKeyEntry(
                            "default",
                            KeyUtils.fromPemEncodedPrivateKey(Files.readString(privateKeyFile)),
                            X509CertificateUtils.certificateListFromPem(Files.readString(certificatesFile)))
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static DefaultTlsContext createDefaultTlsContext(TransportSecurityOptions options,
                                                             AuthorizationMode mode,
                                                             MutableX509TrustManager mutableTrustManager,
                                                             MutableX509KeyManager mutableKeyManager) {
        SSLContext sslContext = new SslContextBuilder()
                .withKeyManagerFactory((ignoredKeystore, ignoredPassword) -> mutableKeyManager)
                .withTrustManagerFactory(
                        ignoredTruststore -> options.getAuthorizedPeers()
                                .map(authorizedPeers -> (X509ExtendedTrustManager) new PeerAuthorizerTrustManager(authorizedPeers, mode, mutableTrustManager))
                                .orElseGet(() -> new PeerAuthorizerTrustManager(new AuthorizedPeers(Set.of()), AuthorizationMode.DISABLE, mutableTrustManager)))
                .build();
        return new DefaultTlsContext(sslContext, options.getAcceptedCiphers());
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
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private class CryptoMaterialReloader implements Runnable {
        @Override
        public void run() {
            try {
                reloadCryptoMaterial(TransportSecurityOptions.fromJsonFile(tlsOptionsConfigFile), trustManager, keyManager);
            } catch (Throwable t) {
                log.log(Level.SEVERE, String.format("Failed to reload crypto material (path='%s'): %s", tlsOptionsConfigFile, t.getMessage()), t);
            }
        }
    }

}
