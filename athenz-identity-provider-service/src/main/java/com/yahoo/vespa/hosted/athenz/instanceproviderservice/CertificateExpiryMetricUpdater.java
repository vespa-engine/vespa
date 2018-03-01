// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.Metric;

import com.google.inject.Inject;

import java.security.KeyStoreException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author freva
 */
public class CertificateExpiryMetricUpdater extends AbstractComponent {

    private static final Duration METRIC_REFRESH_PERIOD = Duration.ofMinutes(5);
    private static final String NODE_CA_CERT_METRIC_NAME = "node-ca-cert.expiry.seconds";
    private static final String ATHENZ_CONFIGSERVER_CERT_METRIC_NAME = "athenz-configserver-cert.expiry.seconds";

    private final Logger logger = Logger.getLogger(CertificateExpiryMetricUpdater.class.getName());
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Metric metric;
    private final AthenzSslKeyStoreConfigurator keyStoreConfigurator;
    private final AthenzSslTrustStoreConfigurator trustStoreConfigurator;

    @Inject
    public CertificateExpiryMetricUpdater(Metric metric,
                                          AthenzSslKeyStoreConfigurator keyStoreConfigurator,
                                          AthenzSslTrustStoreConfigurator trustStoreConfigurator) {
        this.metric = metric;
        this.keyStoreConfigurator = keyStoreConfigurator;
        this.trustStoreConfigurator = trustStoreConfigurator;


        scheduler.scheduleAtFixedRate(this::updateMetrics,
                30/*initial delay*/,
                METRIC_REFRESH_PERIOD.getSeconds(),
                TimeUnit.SECONDS);
    }

    @Override
    public void deconstruct() {
        try {
            scheduler.shutdownNow();
            scheduler.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to shutdown certificate expiry metrics updater on time", e);
        }
    }

    private void updateMetrics() {
        Instant now = Instant.now();

        try {
            Duration keyStoreExpiry = Duration.between(now, keyStoreConfigurator.getCertificateExpiry());
            metric.set(ATHENZ_CONFIGSERVER_CERT_METRIC_NAME, keyStoreExpiry.getSeconds(), null);
        } catch (KeyStoreException e) {
            logger.log(Level.WARNING, "Failed to update key store expiry metric", e);
        }

        try {
            Duration trustStoreExpiry = Duration.between(now, trustStoreConfigurator.getTrustStoreExpiry());
            metric.set(NODE_CA_CERT_METRIC_NAME, trustStoreExpiry.getSeconds(), null);
        } catch (KeyStoreException e) {
            logger.log(Level.WARNING, "Failed to update trust store expiry metric", e);
        }
    }
}
