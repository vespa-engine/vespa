// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProviderException;
import com.yahoo.jdisc.Metric;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.api.AthenzIdentityCertificate;
import com.yahoo.vespa.athenz.tls.SslContextBuilder;
import com.yahoo.vespa.defaults.Defaults;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.yahoo.vespa.athenz.tls.KeyStoreType.JKS;

/**
 * @author mortent
 * @author bjorncs
 */
public final class AthenzIdentityProviderImpl extends AbstractComponent implements AthenzIdentityProvider {

    private static final Logger log = Logger.getLogger(AthenzIdentityProviderImpl.class.getName());

    // TODO Make some of these values configurable through config. Match requested expiration of register/update requests.
    // TODO These should match the requested expiration
    static final Duration UPDATE_PERIOD = Duration.ofDays(1);
    static final Duration AWAIT_TERMINTATION_TIMEOUT = Duration.ofSeconds(90);

    public static final String CERTIFICATE_EXPIRY_METRIC_NAME = "athenz-tenant-cert.expiry.seconds";

    private volatile AthenzCredentials credentials;
    private final Metric metric;
    private final AthenzCredentialsService athenzCredentialsService;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;
    private final String domain;
    private final String service;

    @Inject
    public AthenzIdentityProviderImpl(IdentityConfig config, Metric metric) {
        this(config,
             metric,
             new AthenzCredentialsService(config,
                                          new IdentityDocumentService(config.loadBalancerAddress()),
                                          new AthenzService(),
                                          Clock.systemUTC()),
             new ScheduledThreadPoolExecutor(1),
             Clock.systemUTC());
    }

    // Test only
    AthenzIdentityProviderImpl(IdentityConfig config,
                               Metric metric,
                               AthenzCredentialsService athenzCredentialsService,
                               ScheduledExecutorService scheduler,
                               Clock clock) {
        this.metric = metric;
        this.athenzCredentialsService = athenzCredentialsService;
        this.scheduler = scheduler;
        this.clock = clock;
        this.domain = config.domain();
        this.service = config.service();
        registerInstance();
    }

    private void registerInstance() {
        try {
            credentials = athenzCredentialsService.registerInstance();
            scheduler.scheduleAtFixedRate(this::refreshCertificate, UPDATE_PERIOD.toMinutes(), UPDATE_PERIOD.toMinutes(), TimeUnit.MINUTES);
            scheduler.scheduleAtFixedRate(this::reportMetrics, 0, 5, TimeUnit.MINUTES);
        } catch (Throwable t) {
            throw new AthenzIdentityProviderException("Could not retrieve Athenz credentials", t);
        }
    }

    @Override
    public String getDomain() {
        return domain;
    }

    @Override
    public String getService() {
        return service;
    }

    @Override
    public SSLContext getIdentitySslContext() {
        return new SslContextBuilder()
                .withIdentityCertificate(new AthenzIdentityCertificate(
                        credentials.getCertificate(),
                        credentials.getKeyPair().getPrivate()))
                .withTrustStore(new File(Defaults.getDefaults().underVespaHome("share/ssl/certs/yahoo_certificate_bundle.jks")), JKS)
                .build();
    }

    @Override
    public void deconstruct() {
        try {
            scheduler.shutdownNow();
            scheduler.awaitTermination(AWAIT_TERMINTATION_TIMEOUT.getSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isExpired(AthenzCredentials credentials) {
        return clock.instant().isAfter(getExpirationTime(credentials));
    }

    private static Instant getExpirationTime(AthenzCredentials credentials) {
        return credentials.getCertificate().getNotAfter().toInstant();
    }

    void refreshCertificate() {
        try {
            AthenzCredentials newCredentials = isExpired(credentials)
                    ? athenzCredentialsService.registerInstance()
                    : athenzCredentialsService.updateCredentials(credentials);
            credentials = newCredentials;
        } catch (Throwable t) {
            log.log(LogLevel.WARNING, "Failed to update credentials: " + t.getMessage(), t);
        }
    }

    void reportMetrics() {
        try {
            Instant expirationTime = getExpirationTime(credentials);
            Duration remainingLifetime = Duration.between(clock.instant(), expirationTime);
            metric.set(CERTIFICATE_EXPIRY_METRIC_NAME, remainingLifetime.getSeconds(), null);
        } catch (Throwable t) {
            log.log(LogLevel.WARNING, "Failed to update metrics: " + t.getMessage(), t);
        }
    }
}

