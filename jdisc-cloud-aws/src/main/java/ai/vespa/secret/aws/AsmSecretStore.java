package ai.vespa.secret.aws;

import ai.vespa.secret.config.aws.AsmSecretConfig;
import ai.vespa.secret.internal.TypedSecretStore;
import ai.vespa.secret.model.Key;
import ai.vespa.secret.model.Role;
import ai.vespa.secret.model.Secret;
import ai.vespa.secret.model.SecretVersionId;
import ai.vespa.secret.model.SecretVersionState;
import ai.vespa.secret.model.VaultName;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.yahoo.component.annotation.Inject;
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
 * A read-only client for AWS Secrets Manager, with caching of secrets.
 * Based on ASMSecretStore in hosted-configserver.
 *
 * @author gjoranv
 */
public final class AsmSecretStore extends AsmSecretStoreBase implements TypedSecretStore {

    private static final Duration CACHE_EXPIRE = Duration.ofMinutes(30);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

    private final LoadingCache<VersionKey, Secret> cache;
    private final Runnable closeable;

    protected record VersionKey(Key key, SecretVersionId version) {}

    @Inject
    public AsmSecretStore(AsmSecretConfig config, ServiceIdentityProvider identities) {
        this(URI.create(config.ztsUri()), identities.getIdentitySslContext(), identities.identity().getDomain());
    }

    public AsmSecretStore(URI ztsUri, SSLContext sslContext, AthenzDomain systemDomain) {
        this(new DefaultZtsClient.Builder(ztsUri).withSslContext(sslContext).build(), systemDomain);
    }

    private AsmSecretStore(ZtsClient ztsClient, AthenzDomain systemDomain) {
        super(ztsClient, Role.READER, systemDomain);
        cache = initCache();
        closeable = ztsClient::close;
    }

    // For testing
    AsmSecretStore(Function<VaultName, SecretsManagerClient> clientAndCredentialsSupplier) {
        super(clientAndCredentialsSupplier);
        cache = initCache();
        closeable = () -> {};
    }


    private LoadingCache<VersionKey, Secret> initCache() {
        return CacheBuilder.newBuilder()
                .refreshAfterWrite(CACHE_EXPIRE)
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

        GetSecretValueResponse secret = client.getSecretValue(request);
        return new Secret(key,
                          secret.secretString().getBytes(StandardCharsets.UTF_8),
                          SecretVersionId.of(secret.versionId()),
                          toSecretVersionState(secret.versionStages()));
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
    public Secret getSecret(Key key, SecretVersionId version) {
        try {
            return cache.getUnchecked(new VersionKey(key, version));
        } catch (Exception e) {
            var msg = version == null ?
                    "Failed to retrieve current version of secret with key " + key
                    : "Failed to retrieve secret with key " + key + ", version: " + version.value();
            throw new IllegalArgumentException(msg, e);
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

        ListSecretVersionIdsResponse response = client.listSecretVersionIds(
                ListSecretVersionIdsRequest.builder()
                        .secretId(awsSecretId(key)).build());

        var secretVersions = response.versions().stream()
                .map(version -> getSecret(key, SecretVersionId.of(version.versionId())))
                .sorted().toList();

        secretVersions.forEach(secret -> cache.put(new VersionKey(key, secret.version()), secret));

        return secretVersions;
    }

    @Override
    public Type type() {
        return Type.PUBLIC;
    }

    @Override
    public void close() {
        scheduler.shutdown();
        closeable.run();
        super.close();
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
