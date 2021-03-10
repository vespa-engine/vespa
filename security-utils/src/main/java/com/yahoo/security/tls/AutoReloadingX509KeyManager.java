// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.KeyStoreType;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.security.X509CertificateWithKey;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link X509ExtendedKeyManager} that reloads the certificate and private key from file regularly.
 *
 * @author bjorncs
 */
public class AutoReloadingX509KeyManager extends X509ExtendedKeyManager implements AutoCloseable {

    public static final String CERTIFICATE_ALIAS = "default";

    private static final Duration UPDATE_PERIOD = Duration.ofHours(1);

    private static final Logger log = Logger.getLogger(AutoReloadingX509KeyManager.class.getName());

    private final MutableX509KeyManager mutableX509KeyManager;
    private final ScheduledExecutorService scheduler;
    private final Path privateKeyFile;
    private final Path certificatesFile;

    private AutoReloadingX509KeyManager(Path privateKeyFile, Path certificatesFile) {
        this(privateKeyFile, certificatesFile, createDefaultScheduler());
    }

    AutoReloadingX509KeyManager(Path privateKeyFile, Path certificatesFile, ScheduledExecutorService scheduler) {
            this.privateKeyFile = privateKeyFile;
            this.certificatesFile = certificatesFile;
            this.scheduler = scheduler;
            this.mutableX509KeyManager = new MutableX509KeyManager(createKeystore(privateKeyFile, certificatesFile), new char[0]);
            scheduler.scheduleAtFixedRate(
                    new KeyManagerReloader(), UPDATE_PERIOD.getSeconds()/*initial delay*/, UPDATE_PERIOD.getSeconds(), TimeUnit.SECONDS);
        }

    public static AutoReloadingX509KeyManager fromPemFiles(Path privateKeyFile, Path certificatesFile) {
        return new AutoReloadingX509KeyManager(privateKeyFile, certificatesFile);
    }

    public X509CertificateWithKey getCurrentCertificateWithKey() {
        X509ExtendedKeyManager manager = mutableX509KeyManager.currentManager();
        X509Certificate[] certificateChain = manager.getCertificateChain(CERTIFICATE_ALIAS);
        PrivateKey privateKey = manager.getPrivateKey(CERTIFICATE_ALIAS);
        return new X509CertificateWithKey(Arrays.asList(certificateChain), privateKey);
    }

    private static KeyStore createKeystore(Path privateKey, Path certificateChain) {
        try {
            return KeyStoreBuilder.withType(KeyStoreType.PKCS12)
                    .withKeyEntry(
                            CERTIFICATE_ALIAS,
                            KeyUtils.fromPemEncodedPrivateKey(new String(Files.readAllBytes(privateKey), StandardCharsets.UTF_8)),
                            X509CertificateUtils.certificateListFromPem(new String(Files.readAllBytes(certificateChain), StandardCharsets.UTF_8)))
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ScheduledExecutorService createDefaultScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "auto-reloading-x509-key-manager");
            thread.setDaemon(true);
            return thread;
        });
    }

    private class KeyManagerReloader implements Runnable {
        @Override
        public void run() {
            try {
                log.log(Level.FINE, () -> String.format("Reloading key and certificate chain (private-key='%s', certificates='%s')", privateKeyFile, certificatesFile));
                mutableX509KeyManager.updateKeystore(createKeystore(privateKeyFile, certificatesFile), new char[0]);
            } catch (Throwable t) {
                log.log(Level.SEVERE,
                        String.format("Failed to load X509 key manager (private-key='%s', certificates='%s'): %s",
                                      privateKeyFile, certificatesFile, t.getMessage()),
                        t);
            }
        }
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

    //
    // Methods from X509ExtendedKeyManager
    //

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return mutableX509KeyManager.getServerAliases(keyType, issuers);
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        return mutableX509KeyManager.getClientAliases(keyType, issuers);
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
        return mutableX509KeyManager.chooseServerAlias(keyType, issuers, socket);
    }

    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
        return mutableX509KeyManager.chooseClientAlias(keyType, issuers, socket);
    }

    @Override
    public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
        return mutableX509KeyManager.chooseEngineServerAlias(keyType, issuers, engine);
    }

    @Override
    public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
        return mutableX509KeyManager.chooseEngineClientAlias(keyType, issuers, engine);
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        return mutableX509KeyManager.getCertificateChain(alias);
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        return mutableX509KeyManager.getPrivateKey(alias);
    }

}
