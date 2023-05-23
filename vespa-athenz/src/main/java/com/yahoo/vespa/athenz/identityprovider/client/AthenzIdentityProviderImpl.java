// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import ai.vespa.metrics.ContainerMetrics;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProviderException;
import com.yahoo.jdisc.Metric;
import com.yahoo.security.AutoReloadingX509KeyManager;
import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.MutableX509KeyManager;
import com.yahoo.security.Pkcs10Csr;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.X509CertificateWithKey;
import com.yahoo.vespa.athenz.api.AthenzAccessToken;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.api.ZToken;
import com.yahoo.vespa.athenz.client.zts.DefaultZtsClient;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.vespa.athenz.tls.AthenzX509CertificateUtils;
import com.yahoo.vespa.athenz.utils.SiaUtils;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    static final Duration AWAIT_TERMINTATION_TIMEOUT = Duration.ofSeconds(90);
    private final static Duration ROLE_SSL_CONTEXT_EXPIRY = Duration.ofHours(2);
    // TODO CMS expects 10min or less token ttl. Use 10min default until we have configurable expiry
    private final static Duration ROLE_TOKEN_EXPIRY = Duration.ofMinutes(10);

    // TODO Make path to trust store paths config
    private static final Path CLIENT_TRUST_STORE = Paths.get("/opt/yahoo/share/ssl/certs/yahoo_certificate_bundle.pem");

    public static final String CERTIFICATE_EXPIRY_METRIC_NAME = ContainerMetrics.ATHENZ_TENANT_CERT_EXPIRY_SECONDS.baseName();

    private final Metric metric;
    private final Path trustStore;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;
    private final AthenzService identity;
    private final URI ztsEndpoint;

    private final AutoReloadingX509KeyManager autoReloadingX509KeyManager;
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
        this(config, metric, CLIENT_TRUST_STORE, new ScheduledThreadPoolExecutor(1), Clock.systemUTC(), createAutoReloadingX509KeyManager(config));
    }

    // Test only
    AthenzIdentityProviderImpl(IdentityConfig config,
                               Metric metric,
                               Path trustStore,
                               ScheduledExecutorService scheduler,
                               Clock clock,
                               AutoReloadingX509KeyManager autoReloadingX509KeyManager) {
        this.metric = metric;
        this.trustStore = trustStore;
        this.scheduler = scheduler;
        this.clock = clock;
        this.identity = new AthenzService(config.domain(), config.service());
        this.ztsEndpoint = URI.create(config.ztsUrl());
        this.roleSslCertCache = crateAutoReloadableCache(ROLE_SSL_CONTEXT_EXPIRY, this::requestRoleCertificate, this.scheduler);
        this.roleKeyManagerCache = new HashMap<>();
        this.roleSpecificRoleTokenCache = createCache(ROLE_TOKEN_EXPIRY, this::createRoleToken);
        this.domainSpecificRoleTokenCache = createCache(ROLE_TOKEN_EXPIRY, this::createRoleToken);
        this.domainSpecificAccessTokenCache = createCache(ROLE_TOKEN_EXPIRY, this::createAccessToken);
        this.roleSpecificAccessTokenCache = createCache(ROLE_TOKEN_EXPIRY, this::createAccessToken);
        this.csrGenerator = new CsrGenerator(config.athenzDnsSuffix(), config.configserverIdentityName());
        this.autoReloadingX509KeyManager = autoReloadingX509KeyManager;
        this.identitySslContext = createIdentitySslContext(autoReloadingX509KeyManager, trustStore);
        this.scheduler.scheduleAtFixedRate(this::reportMetrics, 0, 5, TimeUnit.MINUTES);
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
        var copy = this.autoReloadingX509KeyManager.getCurrentCertificateWithKey();
        return new X509CertificateWithKey(copy.certificate(), copy.privateKey());
    }

    @Override public Path certificatePath() { return SiaUtils.getCertificateFile(identity); }

    @Override public Path privateKeyPath() { return SiaUtils.getPrivateKeyFile(identity); }

    @Override
    public SSLContext getRoleSslContext(String domain, String role) {
        try {
            AthenzRole athenzRole = new AthenzRole(new AthenzDomain(domain), role);
            // Make sure to request a certificate which triggers creating a new key manager for this role
            X509Certificate x509Certificate = getRoleCertificate(athenzRole);
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
                    .toList();
            return roleSpecificAccessTokenCache.get(roleList).value();
        } catch (Exception e) {
            throw new AthenzIdentityProviderException("Could not retrieve access token: " + e.getMessage(), e);
        }
    }

    @Override
    public PrivateKey getPrivateKey() {
        return autoReloadingX509KeyManager.getCurrentCertificateWithKey().privateKey();
    }

    @Override
    public Path trustStorePath() {
        return trustStore;
    }

    @Override
    public List<X509Certificate> getIdentityCertificate() {
        return List.of(autoReloadingX509KeyManager.getCurrentCertificateWithKey().certificate());
    }

    @Override
    public X509Certificate getRoleCertificate(String domain, String role) {
        return getRoleCertificate(new AthenzRole(new AthenzDomain(domain), role));
    }

    private X509Certificate getRoleCertificate(AthenzRole athenzRole) {
        try {
            return roleSslCertCache.get(athenzRole);
        } catch (Exception e) {
            throw new AthenzIdentityProviderException("Could not retrieve role certificate: " + e.getMessage(), e);
        }
    }

    private X509Certificate requestRoleCertificate(AthenzRole role) {
        var credentials = autoReloadingX509KeyManager.getCurrentCertificateWithKey();
        var athenzUniqueInstanceId = VespaUniqueInstanceId.fromDottedString(
                AthenzX509CertificateUtils.getInstanceId(credentials.certificate())
                        .orElseThrow()
        );
        var keyPair = KeyUtils.toKeyPair(credentials.privateKey());
        Pkcs10Csr csr = csrGenerator.generateRoleCsr(
                identity, role, athenzUniqueInstanceId, null, keyPair);
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
                        .withKeyEntry("default", autoReloadingX509KeyManager.getCurrentCertificateWithKey().privateKey(), certificate)
                        .build(),
                new char[0]);
    }

    private ZToken createRoleToken(AthenzRole athenzRole) {
        try (ZtsClient client = createZtsClient()) {
            return client.getRoleToken(athenzRole, ROLE_TOKEN_EXPIRY);
        }
    }

    private ZToken createRoleToken(AthenzDomain domain) {
        try (ZtsClient client = createZtsClient()) {
            return client.getRoleToken(domain, ROLE_TOKEN_EXPIRY);
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

    private static AutoReloadingX509KeyManager createAutoReloadingX509KeyManager(IdentityConfig config) {
        var tenantIdentity = new AthenzService(config.domain(), config.service());
        return AutoReloadingX509KeyManager.fromPemFiles(SiaUtils.getPrivateKeyFile(tenantIdentity), SiaUtils.getCertificateFile(tenantIdentity));
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

    private static Instant getExpirationTime(X509Certificate certificate) {
        return certificate.getNotAfter().toInstant();
    }

    void reportMetrics() {
        try {
            Instant expirationTime = getExpirationTime(autoReloadingX509KeyManager.getCurrentCertificateWithKey().certificate());
            Duration remainingLifetime = Duration.between(clock.instant(), expirationTime);
            metric.set(CERTIFICATE_EXPIRY_METRIC_NAME, remainingLifetime.getSeconds(), null);
        } catch (Throwable t) {
            log.log(Level.WARNING, "Failed to update metrics: " + t.getMessage(), t);
        }
    }
}

