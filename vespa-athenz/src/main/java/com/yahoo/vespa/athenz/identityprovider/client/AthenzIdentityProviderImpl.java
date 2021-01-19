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
import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.Pkcs10Csr;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.X509CertificateWithKey;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.security.KeyStoreType.PKCS12;

/**
 * A {@link AthenzIdentityProvider} / {@link ServiceIdentityProvider} component that provides the tenant identity.
 *
 * @author mortent
 * @author bjorncs
 */
// This class should probably not implement ServiceIdentityProvider,
// as that interface is intended for providing the node's identity, not the tenant's application identity.
public final class AthenzIdentityProviderImpl extends AbstractComponent implements AthenzIdentityProvider, ServiceIdentityProvider {

    private static final Logger log = Logger.getLogger(AthenzIdentityProviderImpl.class.getName());

    // TODO Make some of these values configurable through config. Match requested expiration of register/update requests.
    // TODO These should match the requested expiration
    static final Duration UPDATE_PERIOD = Duration.ofDays(1);
    static final Duration AWAIT_TERMINTATION_TIMEOUT = Duration.ofSeconds(90);
    private final static Duration ROLE_SSL_CONTEXT_EXPIRY = Duration.ofHours(2);
    private final static Duration ROLE_TOKEN_EXPIRY = Duration.ofMinutes(30);

    // TODO Make path to trust store paths config
    private static final Path CLIENT_TRUST_STORE = Paths.get("/opt/yahoo/share/ssl/certs/yahoo_certificate_bundle.pem");
    private static final Path ATHENZ_TRUST_STORE = Paths.get("/opt/yahoo/share/ssl/certs/athenz_certificate_bundle.pem");

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
    private final LoadingCache<AthenzRole, X509Certificate> roleSslCertCache;
    private final Map<AthenzRole, MutableX509KeyManager> roleKeyManagerCache;
    private final LoadingCache<AthenzRole, ZToken> roleSpecificRoleTokenCache;
    private final LoadingCache<AthenzDomain, ZToken> domainSpecificRoleTokenCache;
    private final LoadingCache<AthenzDomain, AthenzAccessToken> domainSpecificAccessTokenCache;
    private final LoadingCache<List<AthenzRole>, AthenzAccessToken> roleSpecificAccessTokenCache;
    private final CsrGenerator csrGenerator;

    @Inject
    public AthenzIdentityProviderImpl(IdentityConfig config, Metric metric) {
        this(config,
             metric,
                CLIENT_TRUST_STORE,
             new AthenzCredentialsService(config,
                                          createNodeIdentityProvider(config),
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
        roleSslCertCache = crateAutoReloadableCache(ROLE_SSL_CONTEXT_EXPIRY, this::requestRoleCertificate, this.scheduler);
        roleKeyManagerCache = new HashMap<>();
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

    private static <KEY, VALUE> LoadingCache<KEY, VALUE> crateAutoReloadableCache(Duration expiry, Function<KEY, VALUE> cacheLoader, ScheduledExecutorService scheduler) {
        LoadingCache<KEY, VALUE> cache = createCache(expiry, cacheLoader);

        // The cache above will reload it's contents if and only if a request for the key is made. Scheduling
        // a cache reloader to reload all keys in this cache.
        scheduler.scheduleAtFixedRate(() -> { cache.asMap().keySet().forEach(cache::getUnchecked);},
                                      expiry.dividedBy(4).toMinutes(),
                                      expiry.dividedBy(4).toMinutes(),
                                      TimeUnit.MINUTES);
        return cache;
    }

    private static SSLContext createIdentitySslContext(X509ExtendedKeyManager keyManager, Path trustStore) {
        return new SslContextBuilder()
                .withKeyManager(keyManager)
                .withTrustStore(trustStore)
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
    public X509CertificateWithKey getIdentityCertificateWithKey() {
        AthenzCredentials copy = this.credentials;
        return new X509CertificateWithKey(copy.getCertificate(), copy.getKeyPair().getPrivate());
    }

    @Override public Path certificatePath() { return athenzCredentialsService.certificatePath(); }

    @Override public Path privateKeyPath() { return athenzCredentialsService.privateKeyPath(); }

    @Override public Path athenzTruststorePath() { return ATHENZ_TRUST_STORE; }

    @Override public Path clientTruststorePath() { return CLIENT_TRUST_STORE; }

    @Override
    public SSLContext getRoleSslContext(String domain, String role) {
        try {
            AthenzRole athenzRole = new AthenzRole(new AthenzDomain(domain), role);
            // Make sure to request a certificate which triggers creating a new key manager for this role
            X509Certificate x509Certificate = roleSslCertCache.get(athenzRole);
            MutableX509KeyManager keyManager = roleKeyManagerCache.get(athenzRole);
            return new SslContextBuilder()
                    .withKeyManager(keyManager)
                    .withTrustStore(trustStore)
                    .build();
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
        try {
            return domainSpecificAccessTokenCache.get(new AthenzDomain(domain)).value();
        } catch (Exception e) {
            throw new AthenzIdentityProviderException("Could not retrieve access token: " + e.getMessage(), e);
        }
    }

    @Override
    public String getAccessToken(String domain, List<String> roles) {
        try {
            List<AthenzRole> roleList = roles.stream()
                    .map(roleName -> new AthenzRole(domain, roleName))
                    .collect(Collectors.toList());
            return roleSpecificAccessTokenCache.get(roleList).value();
        } catch (Exception e) {
            throw new AthenzIdentityProviderException("Could not retrieve access token: " + e.getMessage(), e);
        }
    }

    @Override
    public PrivateKey getPrivateKey() {
        return credentials.getKeyPair().getPrivate();
    }

    @Override
    public Path trustStorePath() {
        return trustStore;
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

    private X509Certificate requestRoleCertificate(AthenzRole role) {
        Pkcs10Csr csr = csrGenerator.generateRoleCsr(identity, role, credentials.getIdentityDocument().providerUniqueId(), credentials.getKeyPair());
        try (ZtsClient client = createZtsClient()) {
            X509Certificate roleCertificate = client.getRoleCertificate(role, csr);
            updateRoleKeyManager(role, roleCertificate);
            log.info(String.format("Requester role certificate for role %s, expires: %s", role.toResourceNameString(), roleCertificate.getNotAfter().toInstant().toString()));
            return roleCertificate;
        }
    }

    private void updateRoleKeyManager(AthenzRole role, X509Certificate certificate) {
        MutableX509KeyManager keyManager = roleKeyManagerCache.computeIfAbsent(role, r -> new MutableX509KeyManager());
        keyManager.updateKeystore(
                KeyStoreBuilder.withType(PKCS12)
                        .withKeyEntry("default", credentials.getKeyPair().getPrivate(), certificate)
                        .build(),
                new char[0]);
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
        return new DefaultZtsClient.Builder(ztsEndpoint).withSslContext(getIdentitySslContext()).build();
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

    private static SiaIdentityProvider createNodeIdentityProvider(IdentityConfig config) {
        return new SiaIdentityProvider(
                new AthenzService(config.nodeIdentityName()), SiaUtils.DEFAULT_SIA_DIRECTORY, ATHENZ_TRUST_STORE, CLIENT_TRUST_STORE);
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
            log.log(Level.WARNING, "Failed to update credentials: " + t.getMessage(), t);
        }
    }

    void reportMetrics() {
        try {
            Instant expirationTime = getExpirationTime(credentials);
            Duration remainingLifetime = Duration.between(clock.instant(), expirationTime);
            metric.set(CERTIFICATE_EXPIRY_METRIC_NAME, remainingLifetime.getSeconds(), null);
        } catch (Throwable t) {
            log.log(Level.WARNING, "Failed to update metrics: " + t.getMessage(), t);
        }
    }
}

