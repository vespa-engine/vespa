// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.google.inject.Inject;
import com.yahoo.athenz.auth.impl.PrincipalAuthority;
import com.yahoo.athenz.auth.impl.SimpleServiceIdentityProvider;
import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.athenz.zts.InstanceRefreshRequest;
import com.yahoo.athenz.zts.ZTSClient;
import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.http.ssl.ReaderForPath;
import com.yahoo.jdisc.http.ssl.pem.PemKeyStore;
import com.yahoo.jdisc.http.ssl.pem.PemSslKeyStore;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.athenz.identityproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.FileBackedKeyProvider;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.InstanceValidator;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.KeyProvider;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.ProviderServiceServlet;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.StatusServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.StringReader;
import java.security.KeyStore;
import java.security.PrivateKey;
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
    public AthenzInstanceProviderService(AthenzProviderServiceConfig config) {
        this(config, new FileBackedKeyProvider(config.keyPathPrefix()), Executors.newSingleThreadScheduledExecutor());
    }

    AthenzInstanceProviderService(AthenzProviderServiceConfig config,
                                  KeyProvider keyProvider,
                                  ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
        SslContextFactory sslContextFactory = createSslContextFactory();
        this.jetty = createJettyServer(config.port(), config.apiPath(), keyProvider, sslContextFactory);
        AthenzCertificateUpdater reloader = new AthenzCertificateUpdater(
                sslContextFactory, keyProvider, config);
        scheduler.scheduleAtFixedRate(reloader, 0, 1, TimeUnit.DAYS);
        try {
            jetty.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Server createJettyServer(int port, String apiPath,
                                            KeyProvider keyProvider, SslContextFactory sslContextFactory)  {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server, sslContextFactory);
        connector.setPort(port);
        server.addConnector(connector);

        ServletHandler handler = new ServletHandler();
        ProviderServiceServlet providerServiceServlet =
                new ProviderServiceServlet(new InstanceValidator(keyProvider));
        handler.addServletWithMapping(new ServletHolder(providerServiceServlet), apiPath);
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

        private static final Logger log = Logger.getLogger(AthenzCertificateUpdater.class.getName());

        private final SslContextFactory sslContextFactory;
        private final KeyProvider keyProvider;
        private final AthenzProviderServiceConfig config;

        private AthenzCertificateUpdater(SslContextFactory sslContextFactory,
                                         KeyProvider keyProvider,
                                         AthenzProviderServiceConfig config) {
            this.sslContextFactory = sslContextFactory;
            this.keyProvider = keyProvider;
            this.config = config;
        }

        @Override
        public void run() {
            try {
                log.log(LogLevel.INFO, "Updating Athenz certificate through ZTS");
                String privateKey = keyProvider.getPrivateKey(config.keyVersion());
                String certificate = getCertificateFromZTS(Crypto.loadPrivateKey(privateKey));
                final KeyStore keyStore =
                        new PemSslKeyStore(
                                new PemKeyStore.KeyStoreLoadParameter(
                                        new ReaderForPath(new StringReader(certificate), null),
                                        new ReaderForPath(new StringReader(privateKey), null)))
                                .loadJavaKeyStore();
                sslContextFactory.reload(sslContextFactory -> sslContextFactory.setKeyStore(keyStore));
                log.log(LogLevel.INFO, "Athenz certificate reload successfully completed");
            } catch (Exception e) {
                log.log(LogLevel.ERROR, "Failed to update certificate from ZTS: " + e.getMessage(), e);
            }
        }

        private String getCertificateFromZTS(PrivateKey privateKey) {
            SimpleServiceIdentityProvider identityProvider = new SimpleServiceIdentityProvider(
                    new AthenzPrincipalAuthority(config.athenzPrincipalHeaderName()), config.domain(), config.serviceName(),
                    privateKey, Integer.toString(config.keyVersion()), TimeUnit.MINUTES.toSeconds(10));
            ZTSClient ztsClient = new ZTSClient(
                    config.ztsUrl(), config.domain(), config.serviceName(), identityProvider);
            InstanceRefreshRequest req = ZTSClient.generateInstanceRefreshRequest(
                    config.domain(), config.serviceName(), privateKey, config.certDnsSuffix(), (int)TimeUnit.DAYS.toSeconds(30));
            return ztsClient.postInstanceRefreshRequest(config.domain(), config.serviceName(), req).getCertificate();
        }

        private static class AthenzPrincipalAuthority extends PrincipalAuthority {
            private final String headerName;

            public AthenzPrincipalAuthority(String headerName) {
                this.headerName = headerName;
            }

            @Override
            public String getHeader() {
                return headerName;
            }
        }
    }

    @Override
    public void deconstruct() {
        try {
            log.log(LogLevel.INFO, "Deconstructing Athenz provider service");
            scheduler.shutdown();
            jetty.stop();
            if (!scheduler.awaitTermination(1, TimeUnit.MINUTES)) {
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
