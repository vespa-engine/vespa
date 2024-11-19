// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.secret.aws;

import ai.vespa.secret.config.aws.AsmSecretConfig;
import ai.vespa.secret.internal.TypedSecretStore;
import ai.vespa.secret.model.Key;
import ai.vespa.secret.model.Secret;
import ai.vespa.secret.model.SecretVersionId;
import ai.vespa.secret.model.SecretVersionState;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.client.zts.DefaultZtsClient;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretVersionIdsRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretVersionIdsResponse;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

/**
 * Base class for read-only client for AWS Secrets Manager, with caching of secrets.
 *
 * @author gjoranv
 */
public abstract class AsmSecretReader extends AsmSecretStoreBase
        implements TypedSecretStore {

    private static final Duration DEFAULT_REFRESH_INTERVAL = Duration.ofMinutes(30);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

    private final LoadingCache<VersionKey, Secret> cache;
    private final Runnable ztsClientCloser;
    private final Duration refreshInterval;

    protected record VersionKey(Key key, SecretVersionId version) {}

    // For subclasses using dependency injection
    public AsmSecretReader(AsmSecretConfig config, ServiceIdentityProvider identities) {
        this(ztsClient(URI.create(config.ztsUri()), identities.getIdentitySslContext()),
             athenzDomain(config, identities),
             Duration.ofMinutes(config.refreshInterval()));
    }

    public AsmSecretReader(URI ztsUri, SSLContext sslContext, AthenzDomain domain) {
        this(ztsClient(ztsUri, sslContext), domain, DEFAULT_REFRESH_INTERVAL);
    }

    private AsmSecretReader(ZtsClient ztsClient, AthenzDomain domain, Duration refreshInterval) {
        super(ztsClient, domain);
        this.refreshInterval = refreshInterval;
        cache = initCache();
        ztsClientCloser = ztsClient::close;
    }

    // For testing
    public AsmSecretReader(Function<AssumedRoleInfo, SecretsManagerClient> clientAndCredentialsSupplier) {
        super(clientAndCredentialsSupplier);
        this.refreshInterval = DEFAULT_REFRESH_INTERVAL;
        cache = initCache();
        ztsClientCloser = () -> {};
    }


    /** Returns the AWS secret id to use for the given key. */
    protected abstract String awsSecretId(Key key);


    private static ZtsClient ztsClient(URI ztsUri, SSLContext sslContext) {
        return new DefaultZtsClient.Builder(ztsUri).withSslContext(sslContext).build();
    }

    private static AthenzDomain athenzDomain(AsmSecretConfig config, ServiceIdentityProvider identities) {
        return config.athenzDomain().isEmpty() ?
                identities.identity().getDomain() : new AthenzDomain(config.athenzDomain());
    }

    private LoadingCache<VersionKey, Secret> initCache() {
        return CacheBuilder.newBuilder()
                .refreshAfterWrite(refreshInterval)
                // See documentation for refreshAfterWrite for why we use asyncReloading.
                .build(CacheLoader.asyncReloading(new CacheLoader<>() {
                    @Override
                    public Secret load(VersionKey key) {
                        return retrieveSecret(key.key(), key.version());
                    }
                }, scheduler));
    }

    private Secret retrieveSecret(Key key, SecretVersionId version) {
        var client = getClient(key.vaultName());
        var requestBuilder = GetSecretValueRequest.builder()
                .secretId(awsSecretId(key));

        if (version != null) {
            requestBuilder.versionId(version.value());
        } else {
            requestBuilder.versionStage(AWSCURRENT);
        }
        var request = requestBuilder.build();


        try {
            GetSecretValueResponse secret = client.getSecretValue(request);
            return new Secret(key,
                              secret.secretString().getBytes(StandardCharsets.UTF_8),
                              SecretVersionId.of(secret.versionId()),
                              toSecretVersionState(secret.versionStages()));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to retrieve secret with key %s:\n%s".formatted(key, e.getMessage()));
        }
    }

    private static SecretVersionState toSecretVersionState(List<String> versionStages) {
        if (versionStages.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one version stage, got: " + versionStages);
        }
        var state = versionStages.get(0);
        return switch (state) {
            case "AWSCURRENT" -> SecretVersionState.CURRENT;
            case "AWSPENDING" -> SecretVersionState.PENDING;
            case "AWSPREVIOUS" -> SecretVersionState.PREVIOUS;
            default -> throw new IllegalArgumentException("Unknown secret version state: " + state);
        };
    }

    /**
     * If version is null, the version with label AWSCURRENT is returned.
     */
    @Override
    public Secret getSecret(Key key, SecretVersionId version) {
        try {
            return cache.getUnchecked(new VersionKey(key, version));
        } catch (Exception e) {
            var msg = version == null ?
                    "Failed to retrieve current version of secret with key " + key
                    : "Failed to retrieve secret with key " + key + ", version: " + version.value();
            throw new IllegalArgumentException(msg + ":\n" + e.getMessage());
        }
    }

    @Override
    public Secret getSecret(Key key) {
        return getSecret(key, null);
    }

    /**
     * List all versions of the given secret, sorted according to
     * {@link Secret#compareTo(Secret)}. Always retrieves all versions from ASM,
     * and refreshes the cache for each version.
     */
    @Override
    public List<Secret> listSecretVersions(Key key) {
        var client = getClient(key.vaultName());

        try {
            ListSecretVersionIdsResponse response = client.listSecretVersionIds(
                    ListSecretVersionIdsRequest.builder()
                            .secretId(awsSecretId(key)).build());

            var secretVersions = response.versions().stream()
                    .map(version -> getSecret(key, SecretVersionId.of(version.versionId())))
                    .sorted().toList();

            secretVersions.forEach(secret -> cache.put(new VersionKey(key, secret.version()), secret));

            return secretVersions;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to list secret versions for %s:\n%s".formatted(key, e.getMessage()));
        }
    }

    @Override
    public void close() {
        scheduler.shutdown();
        ztsClientCloser.run();
        super.close();
    }

    @Override
    public Type type() {
        return Type.PUBLIC;
    }

    @Override
    public String getSecret(String key) {
        throw new UnsupportedOperationException("This secret store does not support String lookups.");
    }

    @Override
    public String getSecret(String key, int version) {
        throw new UnsupportedOperationException("This secret store does not support String lookups.");
    }

}
