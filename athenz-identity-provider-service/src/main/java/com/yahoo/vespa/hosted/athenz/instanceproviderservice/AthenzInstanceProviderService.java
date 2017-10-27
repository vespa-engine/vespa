// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.AthenzCertificateClient;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.CertificateClient;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.FileBackedKeyProvider;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.IdentityDocumentGenerator;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.IdentityDocumentServlet;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.InstanceConfirmationServlet;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.InstanceValidator;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.KeyProvider;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.StatusServlet;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
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
    public AthenzInstanceProviderService(AthenzProviderServiceConfig config, NodeRepository nodeRepository, Zone zone) {
        this(config, new FileBackedKeyProvider(config.keyPathPrefix()), Executors.newSingleThreadScheduledExecutor(),
             nodeRepository, zone, new AthenzCertificateClient(config));
    }

    AthenzInstanceProviderService(AthenzProviderServiceConfig config,
                                  KeyProvider keyProvider,
                                  ScheduledExecutorService scheduler,
                                  NodeRepository nodeRepository,
                                  Zone zone,
                                  CertificateClient certificateClient) {
        // TODO: Enable for all systems. Currently enabled for CD system only
        if (SystemName.cd.equals(zone.system())) {
            this.scheduler = scheduler;
            SslContextFactory sslContextFactory = createSslContextFactory();
            this.jetty = createJettyServer(
                    config, keyProvider, sslContextFactory, nodeRepository, zone);
            AthenzCertificateUpdater reloader =
                    new AthenzCertificateUpdater(certificateClient, sslContextFactory, keyProvider, config);
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
                                            KeyProvider keyProvider,
                                            SslContextFactory sslContextFactory,
                                            NodeRepository nodeRepository,
                                            Zone zone)  {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server, sslContextFactory);
        connector.setPort(config.port());
        server.addConnector(connector);

        ServletHandler handler = new ServletHandler();
        InstanceConfirmationServlet instanceConfirmationServlet =
                new InstanceConfirmationServlet(new InstanceValidator(keyProvider));
        handler.addServletWithMapping(new ServletHolder(instanceConfirmationServlet), config.apiPath() + "/instance");

        IdentityDocumentServlet identityDocumentServlet =
                new IdentityDocumentServlet(new IdentityDocumentGenerator(config, nodeRepository, zone, keyProvider));
        handler.addServletWithMapping(new ServletHolder(identityDocumentServlet), config.apiPath() + "/identity-document");

        handler.addServletWithMapping(StatusServlet.class, "/status.html");
        server.setHandler(handler);
        return server;

    }

    private static SslContextFactory createSslContextFactory() {
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

    private static class AthenzCertificateUpdater implements Runnable {

        // TODO Make expiry a configuration parameter
        private static final TemporalAmount EXPIRY_TIME = Duration.ofDays(30);
        private static final Logger log = Logger.getLogger(AthenzCertificateUpdater.class.getName());

        private final CertificateClient certificateClient;
        private final SslContextFactory sslContextFactory;
        private final KeyProvider keyProvider;
        private final AthenzProviderServiceConfig config;

        private AthenzCertificateUpdater(CertificateClient certificateClient,
                                         SslContextFactory sslContextFactory,
                                         KeyProvider keyProvider,
                                         AthenzProviderServiceConfig config) {
            this.certificateClient = certificateClient;
            this.sslContextFactory = sslContextFactory;
            this.keyProvider = keyProvider;
            this.config = config;
        }

        @Override
        public void run() {
            try {
                log.log(LogLevel.INFO, "Updating Athenz certificate through ZTS");
                PrivateKey privateKey = keyProvider.getPrivateKey(config.keyVersion());
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
            if(jetty !=null)
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
