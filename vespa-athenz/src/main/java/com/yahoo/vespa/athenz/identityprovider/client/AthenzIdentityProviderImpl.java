// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProviderException;
import com.yahoo.jdisc.Metric;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProviderListenerHelper;
import com.yahoo.vespa.athenz.tls.KeyStoreType;
import com.yahoo.vespa.athenz.tls.SslContextBuilder;
import com.yahoo.vespa.defaults.Defaults;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author mortent
 * @author bjorncs
 */
public final class AthenzIdentityProviderImpl extends AbstractComponent implements AthenzIdentityProvider, ServiceIdentityProvider {

    private static final Logger log = Logger.getLogger(AthenzIdentityProviderImpl.class.getName());

    // TODO Make some of these values configurable through config. Match requested expiration of register/update requests.
    // TODO These should match the requested expiration
    static final Duration UPDATE_PERIOD = Duration.ofDays(1);
    static final Duration AWAIT_TERMINTATION_TIMEOUT = Duration.ofSeconds(90);

    public static final String CERTIFICATE_EXPIRY_METRIC_NAME = "athenz-tenant-cert.expiry.seconds";

    private volatile AthenzCredentials credentials;
    private final ZtsClient ztsClient = new ZtsClient();
    private final Metric metric;
    private final AthenzCredentialsService athenzCredentialsService;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;
    private final AthenzService identity;
    private final ServiceIdentityProviderListenerHelper listenerHelper;

    // TODO IdentityConfig should contain ZTS uri and dns suffix
    @Inject
    public AthenzIdentityProviderImpl(IdentityConfig config, Metric metric) {
        this(config,
             metric,
             new AthenzCredentialsService(config,
                                          new IdentityDocumentClient(config.loadBalancerAddress()),
                                          new ZtsClient(),
                                          getDefaultTrustStoreLocation()),
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
        this.identity = new AthenzService(config.domain(), config.service());
        this.listenerHelper = new ServiceIdentityProviderListenerHelper(this.identity);
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
    public AthenzService identity() {
        return identity;
    }

    @Override
    public String domain() {
        return identity.getDomain().getName();
    }

    @Override
    public String service() {
        return identity.getName();
    }

    @Override
    public SSLContext getIdentitySslContext() {
        return credentials.getIdentitySslContext();
    }

    @Override
    public void addIdentityListener(Listener listener) {
        listenerHelper.addIdentityListener(listener);
    }

    @Override
    public void removeIdentityListener(Listener listener) {
        listenerHelper.removeIdentityListener(listener);
    }

    @Override
    public SSLContext getRoleSslContext(String domain, String role) {
        // This ssl context should ideally be cached as it is quite expensive to create.
        PrivateKey privateKey = credentials.getKeyPair().getPrivate();
        X509Certificate roleCertificate = ztsClient.getRoleCertificate(
                new AthenzDomain(domain),
                role,
                credentials.getIdentityDocument().dnsSuffix,
                credentials.getIdentityDocument().ztsEndpoint,
                identity,
                privateKey,
                credentials.getIdentitySslContext());
        return new SslContextBuilder()
                .withKeyStore(privateKey, roleCertificate)
                .withTrustStore(getDefaultTrustStoreLocation(), KeyStoreType.JKS)
                .build();
    }

    @Override
    public String getRoleToken(String domain) {
        return ztsClient
                .getRoleToken(
                        new AthenzDomain(domain),
                        credentials.getIdentityDocument().ztsEndpoint,
                        credentials.getIdentitySslContext())
                .getRawToken();
    }

    @Override
    public String getRoleToken(String domain, String role) {
        return ztsClient
                .getRoleToken(
                        new AthenzDomain(domain),
                        role,
                        credentials.getIdentityDocument().ztsEndpoint,
                        credentials.getIdentitySslContext())
                .getRawToken();
    }

    @Override
    public void deconstruct() {
        try {
            scheduler.shutdownNow();
            scheduler.awaitTermination(AWAIT_TERMINTATION_TIMEOUT.getSeconds(), TimeUnit.SECONDS);
            listenerHelper.clearListeners();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static File getDefaultTrustStoreLocation() {
        return new File(Defaults.getDefaults().underVespaHome("share/ssl/certs/yahoo_certificate_bundle.jks"));
    }

    private boolean isExpired(AthenzCredentials credentials) {
        return clock.instant().isAfter(getExpirationTime(credentials));
    }

    private static Instant getExpirationTime(AthenzCredentials credentials) {
        return credentials.getCertificate().getNotAfter().toInstant();
    }

    void refreshCertificate() {
        try {
            credentials = isExpired(credentials)
                    ? athenzCredentialsService.registerInstance()
                    : athenzCredentialsService.updateCredentials(credentials.getIdentityDocument(), credentials.getIdentitySslContext());
            listenerHelper.onCredentialsUpdate(credentials.getIdentitySslContext());
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

