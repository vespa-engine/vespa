// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.model.api.SuperModelProvider;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.http.SecretStore;
import com.yahoo.log.LogLevel;
import com.yahoo.net.HostName;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca.CertificateSigner;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.AthenzCertificateClient;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.CertificateClient;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.IdentityDocumentGenerator;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.InstanceValidator;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.KeyProvider;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.SecretStoreKeyProvider;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.StatusServlet;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * A component acting as both SIA for configserver and provides a lightweight Jetty instance hosting the InstanceConfirmation API
 *
 * @author bjorncs
 */
public class AthenzInstanceProviderService extends AbstractComponent {

    private static final Logger log = Logger.getLogger(AthenzInstanceProviderService.class.getName());

    private final ScheduledExecutorService scheduler;
    private final Server jetty;

    @Inject
    public AthenzInstanceProviderService(AthenzProviderServiceConfig config, SuperModelProvider superModelProvider,
                                         NodeRepository nodeRepository, Zone zone, SecretStore secretStore) {
        this(config, new SecretStoreKeyProvider(secretStore, getZoneConfig(config, zone).secretName()), Executors.newSingleThreadScheduledExecutor(),
             superModelProvider, nodeRepository, zone, new AthenzCertificateClient(config, getZoneConfig(config, zone)), createSslContextFactory());
    }

    private AthenzInstanceProviderService(AthenzProviderServiceConfig config,
                                          KeyProvider keyProvider,
                                          ScheduledExecutorService scheduler,
                                          SuperModelProvider superModelProvider,
                                          NodeRepository nodeRepository,
                                          Zone zone,
                                          CertificateClient certificateClient,
                                          SslContextFactory sslContextFactory) {
        this(config, scheduler, zone, sslContextFactory,
                new CertificateSigner(keyProvider, getZoneConfig(config, zone), HostName.getLocalhost()),
                new InstanceValidator(keyProvider, superModelProvider),
                new IdentityDocumentGenerator(config, getZoneConfig(config, zone), nodeRepository, zone, keyProvider),
                new AthenzCertificateUpdater(
                        certificateClient, sslContextFactory, keyProvider, config, getZoneConfig(config, zone)));
    }

    AthenzInstanceProviderService(AthenzProviderServiceConfig config,
                                  ScheduledExecutorService scheduler,
                                  Zone zone,
                                  SslContextFactory sslContextFactory,
                                  CertificateSigner certificateSigner,
                                  InstanceValidator instanceValidator,
                                  IdentityDocumentGenerator identityDocumentGenerator,
                                  AthenzCertificateUpdater reloader) {
        // TODO: Enable for all systems. Currently enabled for CD system only
        if (SystemName.cd.equals(zone.system())) {
            this.scheduler = scheduler;
            this.jetty = createJettyServer(config, sslContextFactory,
                    certificateSigner, instanceValidator, identityDocumentGenerator);

            // TODO Configurable update frequency
            scheduler.scheduleAtFixedRate(reloader, 0, 1, TimeUnit.DAYS);
            try {
                jetty.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            this.scheduler = null;
            this.jetty = null;
        }
    }

    private static Server createJettyServer(AthenzProviderServiceConfig config,
                                            SslContextFactory sslContextFactory,
                                            CertificateSigner certificateSigner,
                                            InstanceValidator instanceValidator,
                                            IdentityDocumentGenerator identityDocumentGenerator)  {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server, sslContextFactory);
        connector.setPort(config.port());
        server.addConnector(connector);

        ServletHandler handler = new ServletHandler();

        handler.addServletWithMapping(StatusServlet.class, "/status.html");
        server.setHandler(handler);
        return server;

    }

    private static AthenzProviderServiceConfig.Zones getZoneConfig(AthenzProviderServiceConfig config, Zone zone) {
        String key = zone.environment().value() + "." + zone.region().value();
        return config.zones(key);
    }

    static SslContextFactory createSslContextFactory() {
        try {
            SslContextFactory sslContextFactory = new SslContextFactory();
            sslContextFactory.setWantClientAuth(true);
            sslContextFactory.setProtocol("TLS");
            sslContextFactory.setKeyManagerFactoryAlgorithm("SunX509");
            return sslContextFactory;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to create SSL context factory: " + e.getMessage(), e);
        }
    }

    static class AthenzCertificateUpdater implements Runnable {

        // TODO Make expiry a configuration parameter
        private static final TemporalAmount EXPIRY_TIME = Duration.ofDays(30);
        private static final Logger log = Logger.getLogger(AthenzCertificateUpdater.class.getName());

        private final CertificateClient certificateClient;
        private final SslContextFactory sslContextFactory;
        private final KeyProvider keyProvider;
        private final AthenzProviderServiceConfig config;
        private final AthenzProviderServiceConfig.Zones zoneConfig;

        AthenzCertificateUpdater(CertificateClient certificateClient,
                                 SslContextFactory sslContextFactory,
                                 KeyProvider keyProvider,
                                 AthenzProviderServiceConfig config,
                                 AthenzProviderServiceConfig.Zones zoneConfig) {
            this.certificateClient = certificateClient;
            this.sslContextFactory = sslContextFactory;
            this.keyProvider = keyProvider;
            this.config = config;
            this.zoneConfig = zoneConfig;
        }

        @Override
        public void run() {
            try {
                log.log(LogLevel.INFO, "Updating Athenz certificate through ZTS");
                PrivateKey privateKey = keyProvider.getPrivateKey(zoneConfig.secretVersion());
                X509Certificate certificate = certificateClient.updateCertificate(privateKey, EXPIRY_TIME);

                String dummyPassword = "athenz";
                KeyStore keyStore = KeyStore.getInstance("JKS");
                keyStore.load(null);
                keyStore.setKeyEntry("athenz",
                                     privateKey,
                                     dummyPassword.toCharArray(),
                                     new Certificate[]{certificate});

                sslContextFactory.reload(sslContextFactory -> {
                    sslContextFactory.setKeyStore(keyStore);
                    sslContextFactory.setKeyStorePassword(dummyPassword);
                });
                log.log(LogLevel.INFO, "Athenz certificate reload successfully completed");
            } catch (Throwable e) {
                log.log(LogLevel.ERROR, "Failed to update certificate from ZTS: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void deconstruct() {
        try {
            // TODO: Fix deconstruct when setup properly in all zones
            log.log(LogLevel.INFO, "Deconstructing Athenz provider service");
            if(scheduler != null)
                scheduler.shutdown();
            if(jetty != null)
                jetty.stop();
            if (scheduler != null && !scheduler.awaitTermination(1, TimeUnit.MINUTES)) {
                log.log(LogLevel.ERROR, "Failed to stop certificate updater");
            }
        } catch (InterruptedException e) {
            log.log(LogLevel.ERROR, "Failed to stop certificate updater: " + e.getMessage(), e);
        } catch (Exception e) {
            log.log(LogLevel.ERROR, "Failed to stop Jetty: " + e.getMessage(), e);
        } finally {
            super.deconstruct();
        }
    }
}
