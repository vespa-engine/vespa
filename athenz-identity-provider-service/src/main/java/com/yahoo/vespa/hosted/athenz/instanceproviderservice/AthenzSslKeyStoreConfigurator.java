// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.http.SecretStore;
import com.yahoo.jdisc.http.ssl.SslKeyStoreConfigurator;
import com.yahoo.jdisc.http.ssl.SslKeyStoreContext;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.AthenzCertificateClient;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.SecretStoreKeyProvider;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.Utils.getZoneConfig;

/**
 * @author bjorncs
 */
// TODO Cache certificate on disk
public class AthenzSslKeyStoreConfigurator extends AbstractComponent implements SslKeyStoreConfigurator {
    private static final Logger log = Logger.getLogger(AthenzSslKeyStoreConfigurator.class.getName());
    // TODO Make expiry and update frequency configurable parameters
    private static final Duration CERTIFICATE_EXPIRY_TIME = Duration.ofDays(30);
    private static final Duration CERTIFICATE_UPDATE_PERIOD = Duration.ofDays(7);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AthenzCertificateClient certificateClient;
    private final SecretStoreKeyProvider keyProvider;
    private final AthenzProviderServiceConfig.Zones zoneConfig;
    private final AtomicBoolean alreadyConfigured = new AtomicBoolean();
    private final Zone zone;

    @Inject
    public AthenzSslKeyStoreConfigurator(SecretStore secretStore,
                                         AthenzProviderServiceConfig config,
                                         Zone zone) {
        AthenzProviderServiceConfig.Zones zoneConfig = getZoneConfig(config, zone);
        this.certificateClient = new AthenzCertificateClient(config, zoneConfig);
        this.keyProvider = new SecretStoreKeyProvider(secretStore, zoneConfig.secretName());
        this.zoneConfig = zoneConfig;
        this.zone = zone;
    }

    @Override
    public void configure(SslKeyStoreContext sslKeyStoreContext) {
        // TODO Remove this when main is ready
        if (zone.system() != SystemName.cd) {
            return;
        }
        if (alreadyConfigured.getAndSet(true)) { // For debugging purpose of SslKeyStoreConfigurator interface
            throw new IllegalStateException("Already configured. configure() can only be called once.");
        }
        AthenzCertificateUpdater updater = new AthenzCertificateUpdater(sslKeyStoreContext);
        scheduler.scheduleAtFixedRate(updater, /*initialDelay*/0, CERTIFICATE_UPDATE_PERIOD.toMinutes(), TimeUnit.MINUTES);
    }

    @Override
    public void deconstruct() {
        try {
            scheduler.shutdownNow();
            scheduler.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to shutdown Athenz certificate updater on time", e);
        }
    }

    private class AthenzCertificateUpdater implements Runnable {

        private final SslKeyStoreContext sslKeyStoreContext;

        AthenzCertificateUpdater(SslKeyStoreContext sslKeyStoreContext) {
            this.sslKeyStoreContext = sslKeyStoreContext;
        }

        @Override
        public void run() {
            try {
                log.log(LogLevel.INFO, "Updating Athenz certificate from ZTS");
                PrivateKey privateKey = keyProvider.getPrivateKey(zoneConfig.secretVersion());
                X509Certificate certificate = certificateClient.updateCertificate(privateKey, CERTIFICATE_EXPIRY_TIME);

                String dummyPassword = "athenz";
                KeyStore keyStore = KeyStore.getInstance("JKS");
                keyStore.load(null);
                keyStore.setKeyEntry("athenz", privateKey, dummyPassword.toCharArray(), new Certificate[]{certificate});
                sslKeyStoreContext.updateKeyStore(keyStore, dummyPassword);
                log.log(LogLevel.INFO, "Athenz certificate reload successfully completed");
            } catch (Throwable e) {
                log.log(LogLevel.ERROR, "Failed to update certificate from ZTS: " + e.getMessage(), e);
            }
        }
    }
}
