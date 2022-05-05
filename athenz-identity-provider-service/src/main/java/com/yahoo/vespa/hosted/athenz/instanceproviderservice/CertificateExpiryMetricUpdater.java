// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.Metric;

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
    private static final String ATHENZ_CONFIGSERVER_CERT_METRIC_NAME = "athenz-configserver-cert.expiry.seconds";

    private final Logger logger = Logger.getLogger(CertificateExpiryMetricUpdater.class.getName());
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Metric metric;
    private final ConfigserverSslContextFactoryProvider provider;

    @Inject
    public CertificateExpiryMetricUpdater(Metric metric,
                                          ConfigserverSslContextFactoryProvider provider) {
        this.metric = metric;
        this.provider = provider;

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
        try {
            Duration keyStoreExpiry = Duration.between(Instant.now(), provider.getCertificateNotAfter());
            metric.set(ATHENZ_CONFIGSERVER_CERT_METRIC_NAME, keyStoreExpiry.getSeconds(), null);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to update key store expiry metric: " + e.getMessage(), e);
        }
    }
}
