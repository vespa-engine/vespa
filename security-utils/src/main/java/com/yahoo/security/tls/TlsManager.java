// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.KeyStoreType;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

class TlsManager {
    private static final Logger log = Logger.getLogger(TlsManager.class.getName());
    private static final Duration UPDATE_PERIOD = Duration.ofHours(1);

    private final MutableX509TrustManager trustManager;
    private final MutableX509KeyManager keyManager;
    private final ScheduledExecutorService scheduler;
    private TransportSecurityOptions options;

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
                    .withCertificateEntries("cert", X509CertificateUtils.certificateListFromPem(com.yahoo.vespa.jdk8compat.Files.readString(caCertificateFile)))
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
                            KeyUtils.fromPemEncodedPrivateKey(com.yahoo.vespa.jdk8compat.Files.readString(privateKeyFile)),
                            X509CertificateUtils.certificateListFromPem(com.yahoo.vespa.jdk8compat.Files.readString(certificatesFile)))
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    // Note: no reference to outer class (directly or indirectly) to ensure trust/key managers are eventually GCed once
    // there are no more use of the outer class and the underlying SSLContext
    private static class CryptoMaterialReloader implements Runnable {

        private final Path tlsOptionsConfigFile;
        private final ScheduledExecutorService scheduler;
        private final WeakReference<TlsManager> tlsManager;

        CryptoMaterialReloader(Path tlsOptionsConfigFile,
                               ScheduledExecutorService scheduler,
                               TlsManager tlsManager) {
            this.tlsOptionsConfigFile = tlsOptionsConfigFile;
            this.scheduler = scheduler;
            this.tlsManager = new WeakReference<>(tlsManager);
        }

        @Override
        public void run() {
            try {
                TlsManager tlsManager = this.tlsManager.get();
                if (tlsManager == null) {
                    scheduler.shutdown();
                    return;
                }
                TransportSecurityOptions options = TransportSecurityOptions.fromJsonFile(tlsOptionsConfigFile);
                reloadTrustManager(options, tlsManager.getTrustManager());
                MutableX509KeyManager keyManager = tlsManager.getKeyManager();
                reloadKeyManager(options, keyManager);
            } catch (Throwable t) {
                log.log(Level.SEVERE, String.format("Failed to reload crypto material (path='%s'): %s", tlsOptionsConfigFile, t.getMessage()), t);
            }
        }
    }

    // Static class to ensure no reference to outer class is contained
    private static class ReloaderThreadFactory implements ThreadFactory {
        Path fileName;
        ReloaderThreadFactory(Path fileName) {
            this.fileName = fileName;
        }
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "tls-context-reloader:" + fileName.toString() );
            thread.setDaemon(true);
            return thread;
        }
    }

    TlsManager(Path tlsOptionsConfigFile) {
        trustManager = new MutableX509TrustManager();
        keyManager = new MutableX509KeyManager();
        options = TransportSecurityOptions.fromJsonFile(tlsOptionsConfigFile);
        reloadTrustManager(options, trustManager);
        reloadKeyManager(options, keyManager);
        scheduler = Executors.newSingleThreadScheduledExecutor(new ReloaderThreadFactory(tlsOptionsConfigFile));
        this.scheduler.scheduleAtFixedRate(new CryptoMaterialReloader(tlsOptionsConfigFile, scheduler, this),
                UPDATE_PERIOD.getSeconds()/*initial delay*/,
                UPDATE_PERIOD.getSeconds(),
                TimeUnit.SECONDS);
    }
    MutableX509TrustManager getTrustManager() {
        return trustManager;
    }

    MutableX509KeyManager getKeyManager() {
        return keyManager;
    }

    TransportSecurityOptions getOptions() {
        return options;
    }
}
