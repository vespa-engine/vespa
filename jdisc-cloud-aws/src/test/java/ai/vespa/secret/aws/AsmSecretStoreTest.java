package ai.vespa.secret.aws;

import ai.vespa.secret.model.Key;
import ai.vespa.secret.model.Secret;
import ai.vespa.secret.model.SecretName;
import ai.vespa.secret.model.SecretVersionId;
import ai.vespa.secret.model.SecretVersionState;
import ai.vespa.secret.model.VaultName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.InternalServiceErrorException;
import software.amazon.awssdk.services.secretsmanager.model.InvalidNextTokenException;
import software.amazon.awssdk.services.secretsmanager.model.InvalidParameterException;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretVersionIdsRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretVersionIdsResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretVersionsListEntry;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gjoranv
 */
public class AsmSecretStoreTest {

    record SecretVersion(String version, SecretVersionState state, String value) {}

    static final Map<Key, List<SecretVersion>> secrets = new HashMap<>();
    static final List<MockSecretsManagerClient> clients = new ArrayList<>();

    @BeforeEach
    void reset() {
        secrets.clear();
        clients.clear();
    }

    AsmSecretStore createStore() {
        return new AsmSecretStore(MockSecretsManagerClient::new);
    }

    @Test
    void it_creates_one_credentials_and_client_per_vault_and_closes_them() {
        var vault1 = VaultName.of("vault1");
        var vault2 = VaultName.of("vault2");

        var secret1 = new SecretVersion("1", SecretVersionState.CURRENT, "secret1");
        var secret2 = new SecretVersion("2", SecretVersionState.CURRENT, "secret2");

        var key1 = new Key(vault1, SecretName.of("secret1"));
        var key2 = new Key(vault2, SecretName.of("secret2"));

        secrets.put(key1, List.of(secret1));
        secrets.put(key2, List.of(secret2));

        try (var store = createStore()){
            store.getSecret(key1);
            store.getSecret(key2);

            assertEquals(2, clients.size());
        }
        assertTrue(clients.stream().allMatch(c -> c.isClosed));
    }

    @Test
    void it_returns_secret_with_given_version() {
        var vault = VaultName.of("vault1");
        var key = new Key(vault, SecretName.of("secret1"));

        var expected1 = new SecretVersion("1", SecretVersionState.CURRENT, "v1");
        var expected2 = new SecretVersion("2", SecretVersionState.PENDING, "v2");
        secrets.put(key, List.of(expected1, expected2));

        try (var store = createStore()) {
            var retrieved1 = store.getSecret(key, SecretVersionId.of("1"));
            var retrieved2 = store.getSecret(key, SecretVersionId.of("2"));

            assertSame(expected1, retrieved1);
            assertSame(expected2, retrieved2);
        }
    }

    @Test
    void it_returns_current_version_if_no_version_is_given() {
        var vault = VaultName.of("vault1");
        var key = new Key(vault, SecretName.of("secret1"));

        var current = new SecretVersion("1", SecretVersionState.CURRENT, "current");
        var pending = new SecretVersion("2", SecretVersionState.PENDING, "pending");
        secrets.put(key, List.of(current, pending));

        try (var store = createStore()) {
            var retrieved = store.getSecret(key);
            assertSame(current, retrieved);
        }
    }

    @Test
    void it_throws_exception_if_secret_not_found() {
        var vault = VaultName.of("vault1");
        var key = new Key(vault, SecretName.of("secret1"));
        try (var store = createStore()) {
            var e = assertThrows(IllegalArgumentException.class, () -> store.getSecret(key));
            assertEquals("Failed to retrieve current version of secret with key vault1/secret1", e.getMessage());

            e = assertThrows(IllegalArgumentException.class, () -> store.getSecret(key, SecretVersionId.of("1")));
            assertEquals("Failed to retrieve secret with key vault1/secret1, version: 1", e.getMessage());
        }
    }

    @Test
    void it_throws_exception_if_version_not_found() {
        var vault = VaultName.of("vault1");
        var key = new Key(vault, SecretName.of("secret1"));

        var v1 = new SecretVersion("1", SecretVersionState.CURRENT, "v1");
        secrets.put(key, List.of(v1));

        try (var store = createStore()) {
            var e = assertThrows(IllegalArgumentException.class, () -> store.getSecret(key, SecretVersionId.of("2")));
            assertEquals("Failed to retrieve secret with key vault1/secret1, version: 2", e.getMessage());
        }

    }

    @Test
    void it_lists_secret_versions_in_sorted_order() {
        var vault = VaultName.of("vault1");
        var key = new Key(vault, SecretName.of("secret1"));

        var prev = new SecretVersion("1", SecretVersionState.PREVIOUS, "v1");
        var curr = new SecretVersion("2", SecretVersionState.CURRENT, "v2");
        var pend = new SecretVersion("3", SecretVersionState.PENDING, "v3");

        secrets.put(key, List.of(prev, pend, curr));
        try (var store = createStore()) {
            var versions = store.listSecretVersions(key);
            assertEquals(3, versions.size());
            assertSame(pend, versions.get(0));
            assertSame(curr, versions.get(1));
            assertSame(prev, versions.get(2));
        }
    }

    @Test
    void it_returns_empty_list_of_versions_for_unknown_secret() {
        var vault = VaultName.of("vault1");
        var key = new Key(vault, SecretName.of("secret1"));
        try (var store = createStore()) {
            var versions = store.listSecretVersions(key);
            assertEquals(0, versions.size());
        }
    }

    private void assertSame(SecretVersion version, Secret secret) {
        assertEquals(version.value(), secret.secretAsString());
        assertEquals(version.version(), secret.version().value());
    }

    private static Key toKey(String awsId) {
        var parts = awsId.split("/");
        return new Key(VaultName.of(parts[0]), SecretName.of(parts[1]));
    }

    private static class MockSecretsManagerClient implements SecretsManagerClient {
        final VaultName vault;
        boolean isClosed = false;

        MockSecretsManagerClient(VaultName vault) {
            this.vault = vault;
            clients.add(this);
        }

        @Override
        public GetSecretValueResponse getSecretValue(GetSecretValueRequest request) {
            String id = request.secretId();
            String reqVersion = request.versionId();

            var versions = secrets.get(toKey(id));
            if (versions == null) {
                throw ResourceNotFoundException.builder().message("Secret not found").build();
            }
            var secret = findSecret(versions, reqVersion);
            return GetSecretValueResponse.builder()
                    .name(request.secretId())
                    .secretString(secret.value())
                    .versionId(secret.version)
                    .versionStages(List.of(toAwsStage(secret.state)))
                    .build();
        }

        SecretVersion findSecret(List<SecretVersion> versions, String reqVersion) {
            return versions.stream()
                    .filter(reqVersion == null ?
                                    v -> v.state() == SecretVersionState.CURRENT
                                    : v -> v.version().equals(reqVersion))
                    .findFirst()
                    .orElseThrow(() -> ResourceNotFoundException.builder().message("Version not found: " + reqVersion).build());
        }

        @Override
        public ListSecretVersionIdsResponse listSecretVersionIds(ListSecretVersionIdsRequest request) throws InvalidNextTokenException, ResourceNotFoundException, InternalServiceErrorException, InvalidParameterException, AwsServiceException, SdkClientException, SecretsManagerException {
            return ListSecretVersionIdsResponse.builder()
                    .name(request.secretId())
                    .versions(secrets.getOrDefault(toKey(request.secretId()), List.of()).stream()
                                      .map(version -> SecretVersionsListEntry.builder()
                                              .versionId(version.version())
                                              .versionStages(List.of(toAwsStage(version.state())))
                                              .build())
                                      .toList())
                    .build();
        }

        private String toAwsStage(SecretVersionState state) {
            return switch(state) {
                case CURRENT -> "AWSCURRENT";
                case PENDING -> "AWSPENDING";
                case PREVIOUS -> "AWSPREVIOUS";
                default -> throw new IllegalArgumentException("Unknown state: " + state);
            };
        }

        @Override
        public String serviceName() {
            return MockSecretsManagerClient.class.getSimpleName();
        }

        @Override
        public void close() {
            isClosed = true;
        }
    }

}
