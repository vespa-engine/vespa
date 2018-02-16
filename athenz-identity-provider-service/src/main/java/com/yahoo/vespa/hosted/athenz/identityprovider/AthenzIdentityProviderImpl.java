// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.identityprovider;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProviderException;
import com.yahoo.jdisc.Metric;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.api.AthenzIdentityCertificate;
import com.yahoo.vespa.athenz.tls.AthenzSslContextBuilder;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * @author mortent
 * @author bjorncs
 */
public final class AthenzIdentityProviderImpl extends AbstractComponent implements AthenzIdentityProvider {

    private static final Logger log = Logger.getLogger(AthenzIdentityProviderImpl.class.getName());

    // TODO Make some of these values configurable through config. Match requested expiration of register/update requests.
    // TODO These should match the requested expiration
    static final Duration EXPIRES_AFTER = Duration.ofDays(1);
    static final Duration EXPIRATION_MARGIN = Duration.ofMinutes(30);
    static final Duration INITIAL_WAIT_NTOKEN = Duration.ofMinutes(5);
    static final Duration UPDATE_PERIOD = EXPIRES_AFTER.dividedBy(3);
    static final Duration REDUCED_UPDATE_PERIOD = Duration.ofMinutes(30);
    static final Duration INITIAL_BACKOFF_DELAY = Duration.ofMinutes(4);
    static final Duration MAX_REGISTER_BACKOFF_DELAY = Duration.ofHours(1);
    static final int BACKOFF_DELAY_MULTIPLIER = 2;
    static final Duration AWAIT_TERMINTATION_TIMEOUT = Duration.ofSeconds(90);

    private static final Duration CERTIFICATE_EXPIRY_METRIC_UPDATE_PERIOD = Duration.ofMinutes(5);
    private static final String CERTIFICATE_EXPIRY_METRIC_NAME = "athenz-tenant-cert.expiry.seconds";

    static final String REGISTER_INSTANCE_TAG = "register-instance";
    static final String UPDATE_CREDENTIALS_TAG = "update-credentials";
    static final String TIMEOUT_INITIAL_WAIT_TAG = "timeout-initial-wait";
    static final String METRICS_UPDATER_TAG = "metrics-updater";


    private volatile AthenzCredentials credentials;
    private final AtomicReference<Throwable> lastThrowable = new AtomicReference<>();
    private final AthenzCredentialsService athenzCredentialsService;
    private final Scheduler scheduler;
    private final Clock clock;
    private final String domain;
    private final String service;

    private final CertificateExpiryMetricUpdater metricUpdater;

    @Inject
    public AthenzIdentityProviderImpl(IdentityConfig config, Metric metric) {
        this(config,
             metric,
             new AthenzCredentialsService(config,
                                          new IdentityDocumentService(config.loadBalancerAddress()),
                                          new AthenzService(),
                                          Clock.systemUTC()),
             new ThreadPoolScheduler(),
             Clock.systemUTC());
    }

    // Test only
    AthenzIdentityProviderImpl(IdentityConfig config,
                               Metric metric,
                               AthenzCredentialsService athenzCredentialsService,
                               Scheduler scheduler,
                               Clock clock) {
        this.athenzCredentialsService = athenzCredentialsService;
        this.scheduler = scheduler;
        this.clock = clock;
        this.domain = config.domain();
        this.service = config.service();
        metricUpdater = new CertificateExpiryMetricUpdater(metric);
        registerInstance();
    }

    private void registerInstance() {
        try {
            credentials = athenzCredentialsService.registerInstance();
            scheduler.schedule(new UpdateCredentialsTask(), UPDATE_PERIOD);
            scheduler.submit(metricUpdater);
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
        return new AthenzSslContextBuilder()
                .withIdentityCertificate(new AthenzIdentityCertificate(
                        credentials.getCertificate(),
                        credentials.getKeyPair().getPrivate()))
                .withTrustStore(new File("/opt/yahoo/share/ssl/certs/yahoo_certificate_bundle.jks"), "JKS")
                .build();
    }

    @Override
    public void deconstruct() {
        scheduler.shutdown(AWAIT_TERMINTATION_TIMEOUT);
    }

    private boolean isExpired(AthenzCredentials credentials) {
        return clock.instant().isAfter(getExpirationTime(credentials));
    }

    private static Instant getExpirationTime(AthenzCredentials credentials) {
        return credentials.getCreatedAt().plus(EXPIRES_AFTER).minus(EXPIRATION_MARGIN);
    }

    private class UpdateCredentialsTask implements RunnableWithTag {
        @Override
        public void run() {
            try {
                AthenzCredentials newCredentials = isExpired(credentials)
                        ? athenzCredentialsService.registerInstance()
                        : athenzCredentialsService.updateCredentials(credentials);
                credentials = newCredentials;
                scheduler.schedule(new UpdateCredentialsTask(), UPDATE_PERIOD);
            } catch (Throwable t) {
                log.log(LogLevel.WARNING, "Failed to update credentials: " + t.getMessage(), t);
                lastThrowable.set(t);
                Duration timeToExpiration = Duration.between(clock.instant(), getExpirationTime(credentials));
                // NOTE: Update period might be after timeToExpiration, still we do not want to DDoS Athenz.
                Duration updatePeriod =
                        timeToExpiration.compareTo(UPDATE_PERIOD) > 0 ? UPDATE_PERIOD : REDUCED_UPDATE_PERIOD;
                scheduler.schedule(new UpdateCredentialsTask(), updatePeriod);
            }
        }

        @Override
        public String tag() {
            return UPDATE_CREDENTIALS_TAG;
        }
    }

    private class CertificateExpiryMetricUpdater implements RunnableWithTag {
        private final Metric metric;

        private CertificateExpiryMetricUpdater(Metric metric) {
            this.metric = metric;
        }

        @Override
        public void run() {
            Instant expirationTime = getExpirationTime(credentials);
            Duration remainingLifetime = Duration.between(clock.instant(), expirationTime);
            metric.set(CERTIFICATE_EXPIRY_METRIC_NAME, remainingLifetime.getSeconds(), null);
            scheduler.schedule(this, CERTIFICATE_EXPIRY_METRIC_UPDATE_PERIOD);
        }

        @Override
        public String tag() {
            return METRICS_UPDATER_TAG;
        }
    }

    private static class ThreadPoolScheduler implements Scheduler {

        private static final Logger log = Logger.getLogger(ThreadPoolScheduler.class.getName());

        private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(0);

        @Override
        public void schedule(RunnableWithTag runnable, Duration delay) {
            log.log(LogLevel.FINE, String.format("Scheduling task '%s' in '%s'", runnable.tag(), delay));
            executor.schedule(runnable, delay.getSeconds(), TimeUnit.SECONDS);
        }

        @Override
        public void submit(RunnableWithTag runnable) {
            log.log(LogLevel.FINE, String.format("Scheduling task '%s' now", runnable.tag()));
            executor.submit(runnable);
        }

        @Override
        public void shutdown(Duration timeout) {
            try {
                executor.shutdownNow();
                executor.awaitTermination(AWAIT_TERMINTATION_TIMEOUT.getSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public interface Scheduler {
        void schedule(RunnableWithTag runnable, Duration delay);
        default void submit(RunnableWithTag runnable) { schedule(runnable, Duration.ZERO); }
        default void shutdown(Duration timeout) {}
    }

    public interface RunnableWithTag extends Runnable {

        String tag();
    }

}

