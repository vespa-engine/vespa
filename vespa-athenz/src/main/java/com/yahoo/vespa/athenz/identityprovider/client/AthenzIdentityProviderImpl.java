// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProviderException;
import com.yahoo.jdisc.Metric;
import com.yahoo.log.LogLevel;
import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.KeyStoreType;
import com.yahoo.security.Pkcs10Csr;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.tls.MutableX509KeyManager;
import com.yahoo.vespa.athenz.api.AthenzAccessToken;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.api.ZToken;
import com.yahoo.vespa.athenz.client.zts.DefaultZtsClient;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.identity.SiaIdentityProvider;
import com.yahoo.vespa.athenz.utils.SiaUtils;
import com.yahoo.vespa.defaults.Defaults;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedKeyManager;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;

import static com.yahoo.security.KeyStoreType.JKS;
import static com.yahoo.security.KeyStoreType.PKCS12;

/**
 * A {@link AthenzIdentityProvider} / {@link ServiceIdentityProvider} component that provides the tenant identity.
 *
 * @author mortent
 * @author bjorncs
 */
public final class AthenzIdentityProviderImpl extends AbstractComponent implements AthenzIdentityProvider, ServiceIdentityProvider {

    private static final Logger log = Logger.getLogger(AthenzIdentityProviderImpl.class.getName());

    // TODO Make some of these values configurable through config. Match requested expiration of register/update requests.
    // TODO These should match the requested expiration
    static final Duration UPDATE_PERIOD = Duration.ofDays(1);
    static final Duration AWAIT_TERMINTATION_TIMEOUT = Duration.ofSeconds(90);
    private final static Duration ROLE_SSL_CONTEXT_EXPIRY = Duration.ofHours(24);
    private final static Duration ROLE_TOKEN_EXPIRY = Duration.ofMinutes(30);

    // TODO Make path to trust store config
    private static final Path DEFAULT_TRUST_STORE = Paths.get("/opt/yahoo/share/ssl/certs/yahoo_certificate_bundle.jks");

    public static final String CERTIFICATE_EXPIRY_METRIC_NAME = "athenz-tenant-cert.expiry.seconds";

    private volatile AthenzCredentials credentials;
    private final Metric metric;
    private final Path trustStore;
    private final AthenzCredentialsService athenzCredentialsService;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;
    private final AthenzService identity;
    private final URI ztsEndpoint;

    private final MutableX509KeyManager identityKeyManager = new MutableX509KeyManager();
    private final SSLContext identitySslContext;
    private final LoadingCache<AthenzRole, SSLContext> roleSslContextCache;
    private final LoadingCache<AthenzRole, ZToken> roleSpecificRoleTokenCache;
    private final LoadingCache<AthenzDomain, ZToken> domainSpecificRoleTokenCache;
    private final LoadingCache<AthenzDomain, AthenzAccessToken> domainSpecificAccessTokenCache;
    private final LoadingCache<List<AthenzRole>, AthenzAccessToken> roleSpecificAccessTokenCache;
    private final CsrGenerator csrGenerator;

    @Inject
    public AthenzIdentityProviderImpl(IdentityConfig config, Metric metric) {
        this(config,
             metric,
             DEFAULT_TRUST_STORE,
             new AthenzCredentialsService(config,
                                          createNodeIdentityProvider(config, DEFAULT_TRUST_STORE),
                                          Defaults.getDefaults().vespaHostname(),
                                          Clock.systemUTC()),
             new ScheduledThreadPoolExecutor(1),
             Clock.systemUTC());
    }

    // Test only
    AthenzIdentityProviderImpl(IdentityConfig config,
                               Metric metric,
                               Path trustStore,
                               AthenzCredentialsService athenzCredentialsService,
                               ScheduledExecutorService scheduler,
                               Clock clock) {
        this.metric = metric;
        this.trustStore = trustStore;
        this.athenzCredentialsService = athenzCredentialsService;
        this.scheduler = scheduler;
        this.clock = clock;
        this.identity = new AthenzService(config.domain(), config.service());
        this.ztsEndpoint = URI.create(config.ztsUrl());
        roleSslContextCache = createCache(ROLE_SSL_CONTEXT_EXPIRY, this::createRoleSslContext);
        roleSpecificRoleTokenCache = createCache(ROLE_TOKEN_EXPIRY, this::createRoleToken);
        domainSpecificRoleTokenCache = createCache(ROLE_TOKEN_EXPIRY, this::createRoleToken);
        domainSpecificAccessTokenCache = createCache(ROLE_TOKEN_EXPIRY, this::createAccessToken);
        roleSpecificAccessTokenCache = createCache(ROLE_TOKEN_EXPIRY, this::createAccessToken);
        this.csrGenerator = new CsrGenerator(config.athenzDnsSuffix(), config.configserverIdentityName());
        this.identitySslContext = createIdentitySslContext(identityKeyManager, trustStore);
        registerInstance();
    }

    private static <KEY, VALUE> LoadingCache<KEY, VALUE> createCache(Duration expiry, Function<KEY, VALUE> cacheLoader) {
        return CacheBuilder.newBuilder()
                .refreshAfterWrite(expiry.dividedBy(2).toMinutes(), TimeUnit.MINUTES)
                .expireAfterWrite(expiry.toMinutes(), TimeUnit.MINUTES)
                .build(new CacheLoader<KEY, VALUE>() {
                    @Override
                    public VALUE load(KEY key) {
                        return cacheLoader.apply(key);
                    }
                });
    }

    private static SSLContext createIdentitySslContext(X509ExtendedKeyManager keyManager, Path trustStore) {
        return new SslContextBuilder()
                .withKeyManager(keyManager)
                .withTrustStore(trustStore, JKS)
                .build();
    }

    private void registerInstance() {
        try {
            updateIdentityCredentials(this.athenzCredentialsService.registerInstance());
            this.scheduler.scheduleAtFixedRate(this::refreshCertificate, UPDATE_PERIOD.toMinutes(), UPDATE_PERIOD.toMinutes(), TimeUnit.MINUTES);
            this.scheduler.scheduleAtFixedRate(this::reportMetrics, 0, 5, TimeUnit.MINUTES);
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
        return identitySslContext;
    }

    @Override
    public SSLContext getRoleSslContext(String domain, String role) {
        // This ssl context should ideally be cached as it is quite expensive to create.
        try {
            return roleSslContextCache.get(new AthenzRole(new AthenzDomain(domain), role));
        } catch (Exception e) {
            throw new AthenzIdentityProviderException("Could not retrieve role certificate: " + e.getMessage(), e);
        }
    }

    @Override
    public String getRoleToken(String domain) {
        try {
            return domainSpecificRoleTokenCache.get(new AthenzDomain(domain)).getRawToken();
        } catch (Exception e) {
            throw new AthenzIdentityProviderException("Could not retrieve role token: " + e.getMessage(), e);
        }
    }

    @Override
    public String getRoleToken(String domain, String role) {
        try {
            return roleSpecificRoleTokenCache.get(new AthenzRole(domain, role)).getRawToken();
        } catch (Exception e) {
            throw new AthenzIdentityProviderException("Could not retrieve role token: " + e.getMessage(), e);
        }
    }

    @Override
    public String getAccessToken(String domain) {
        return null;
    }

    @Override
    public String getAccessToken(String domain, List<String> roles) {
        return null;
    }

    @Override
    public PrivateKey getPrivateKey() {
        return credentials.getKeyPair().getPrivate();
    }

    @Override
    public List<X509Certificate> getIdentityCertificate() {
        return Collections.singletonList(credentials.getCertificate());
    }

    private void updateIdentityCredentials(AthenzCredentials credentials) {
        this.credentials = credentials;
        this.identityKeyManager.updateKeystore(
                KeyStoreBuilder.withType(PKCS12)
                        .withKeyEntry("default", credentials.getKeyPair().getPrivate(), credentials.getCertificate())
                        .build(),
                new char[0]);
    }

    private SSLContext createRoleSslContext(AthenzRole role) {
        Pkcs10Csr csr = csrGenerator.generateRoleCsr(identity, role, credentials.getIdentityDocument().providerUniqueId(), credentials.getKeyPair());
        try (ZtsClient client = createZtsClient()) {
            X509Certificate roleCertificate = client.getRoleCertificate(role, csr);
            return new SslContextBuilder()
                    .withKeyStore(credentials.getKeyPair().getPrivate(), roleCertificate)
                    .withTrustStore(trustStore, KeyStoreType.JKS)
                    .build();
        }
    }

    private ZToken createRoleToken(AthenzRole athenzRole) {
        try (ZtsClient client = createZtsClient()) {
            return client.getRoleToken(athenzRole);
        }
    }

    private ZToken createRoleToken(AthenzDomain domain) {
        try (ZtsClient client = createZtsClient()) {
            return client.getRoleToken(domain);
        }
    }

    private AthenzAccessToken createAccessToken(AthenzDomain domain) {
        try (ZtsClient client = createZtsClient()) {
            return client.getAccessToken(domain);
        }
    }

    private AthenzAccessToken createAccessToken(List<AthenzRole> roles) {
        try (ZtsClient client = createZtsClient()) {
            return client.getAccessToken(roles);
        }
    }

    private DefaultZtsClient createZtsClient() {
        return new DefaultZtsClient(ztsEndpoint, getIdentitySslContext());
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

    private static SiaIdentityProvider createNodeIdentityProvider(IdentityConfig config, Path trustStore) {
        return new SiaIdentityProvider(
                new AthenzService(config.nodeIdentityName()), SiaUtils.DEFAULT_SIA_DIRECTORY, trustStore.toFile());
    }

    private boolean isExpired(AthenzCredentials credentials) {
        return clock.instant().isAfter(getExpirationTime(credentials));
    }

    private static Instant getExpirationTime(AthenzCredentials credentials) {
        return credentials.getCertificate().getNotAfter().toInstant();
    }

    void refreshCertificate() {
        try {
            updateIdentityCredentials(isExpired(credentials)
                                      ? athenzCredentialsService.registerInstance()
                                      : athenzCredentialsService.updateCredentials(credentials.getIdentityDocument(), identitySslContext));
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

