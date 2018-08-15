// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identity;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.tls.KeyStoreType;
import com.yahoo.vespa.athenz.tls.SslContextBuilder;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;
import com.yahoo.vespa.athenz.utils.SiaUtils;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * A {@link ServiceIdentityProvider} that provides the credentials stored on file system.
 *
 * @author mortent
 * @author bjorncs
 */
public class SiaIdentityProvider extends AbstractComponent implements ServiceIdentityProvider {

    private static final Logger log = Logger.getLogger(SiaIdentityProvider.class.getName());

    private static final Duration REFRESH_INTERVAL = Duration.ofHours(1);

    private final AtomicReference<SSLContext> sslContext = new AtomicReference<>();
    private final AthenzService service;
    private final File privateKeyFile;
    private final File certificateFile;
    private final File trustStoreFile;
    private final ScheduledExecutorService scheduler;
    private final ServiceIdentityProviderListenerHelper listenerHelper;

    @Inject
    public SiaIdentityProvider(SiaProviderConfig config) {
        this(new AthenzService(config.athenzDomain(), config.athenzService()),
             SiaUtils.getPrivateKeyFile(Paths.get(config.keyPathPrefix()), new AthenzService(config.athenzDomain(), config.athenzService())).toFile(),
             SiaUtils.getCertificateFile(Paths.get(config.keyPathPrefix()), new AthenzService(config.athenzDomain(), config.athenzService())).toFile(),
             new File(config.trustStorePath()),
             createScheduler());
    }

    public SiaIdentityProvider(AthenzService service,
                               Path siaPath,
                               File trustStoreFile) {
        this(service,
             SiaUtils.getPrivateKeyFile(siaPath, service).toFile(),
             SiaUtils.getCertificateFile(siaPath, service).toFile(),
             trustStoreFile,
             createScheduler());
    }

    public SiaIdentityProvider(AthenzService service,
                               File privateKeyFile,
                               File certificateFile,
                               File trustStoreFile,
                               ScheduledExecutorService scheduler) {
        this.service = service;
        this.privateKeyFile = privateKeyFile;
        this.certificateFile = certificateFile;
        this.trustStoreFile = trustStoreFile;
        this.scheduler = scheduler;
        this.sslContext.set(createIdentitySslContext());
        this.listenerHelper = new ServiceIdentityProviderListenerHelper(service);
        scheduler.scheduleAtFixedRate(this::reloadSslContext, REFRESH_INTERVAL.toMinutes(), REFRESH_INTERVAL.toMinutes(), TimeUnit.MINUTES);
    }

    private static ScheduledThreadPoolExecutor createScheduler() {
        return new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("sia-identity-provider-sslcontext-updater");
            return thread;
        });
    }

    @Override
    public AthenzService identity() {
        return service;
    }

    @Override
    public SSLContext getIdentitySslContext() {
        return sslContext.get();
    }

    @Override
    public void addIdentityListener(Listener listener) {
        listenerHelper.addIdentityListener(listener);
    }

    @Override
    public void removeIdentityListener(Listener listener) {
        listenerHelper.removeIdentityListener(listener);
    }

    private SSLContext createIdentitySslContext() {
        return new SslContextBuilder()
                .withTrustStore(trustStoreFile, KeyStoreType.JKS)
                .withKeyStore(privateKeyFile, certificateFile)
                .build();
    }

    private void reloadSslContext() {
        log.log(LogLevel.DEBUG, "Updating SSLContext for identity " + service.getFullName());
        try {
            SSLContext sslContext = createIdentitySslContext();
            this.sslContext.set(sslContext);
            listenerHelper.onCredentialsUpdate(sslContext);
        } catch (Exception e) {
            log.log(LogLevel.SEVERE, "Failed to update SSLContext: " + e.getMessage(), e);
        }
    }


    @Override
    public void deconstruct() {
        try {
            scheduler.shutdownNow();
            scheduler.awaitTermination(90, TimeUnit.SECONDS);
            listenerHelper.clearListeners();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
