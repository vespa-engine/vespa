package ai.vespa.secret.aws;

import ai.vespa.secret.aws.testutil.AsmSecretStoreTester;
import ai.vespa.secret.aws.testutil.AsmSecretStoreTester.SecretVersion;
import ai.vespa.secret.model.Key;
import ai.vespa.secret.model.Secret;
import ai.vespa.secret.model.SecretName;
import ai.vespa.secret.model.SecretVersionId;
import ai.vespa.secret.model.SecretVersionState;
import ai.vespa.secret.model.VaultName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gjoranv
 */
public class AsmTenantSecretReaderTest {

    AsmSecretStoreTester tester = new AsmSecretStoreTester(this::awsSecretId);
    
    String system = "publiccd";
    String tenant = "tenant1";

    // The mapping from Key to AWS secret id for tenant secrets
    // Do NOT copy the production code here, as we want to fail upon changes.
    private String awsSecretId(Key key) {
        return "tenant-secret.%s.%s.%s/%s".formatted(
                system, tenant, key.vaultName().value(), key.secretName().value());
    }

    @BeforeEach
    void reset() {
        tester.reset();
    }

    AsmTenantSecretReader secretReader() {
        return new AsmTenantSecretReader(tester::newClient, system, tenant);
    }

    @Test
    void it_creates_one_credentials_and_client_per_vault_and_closes_them() {
        var vault1 = VaultName.of("vault1");
        var vault2 = VaultName.of("vault2");

        var secret1 = new SecretVersion("1", SecretVersionState.CURRENT, "secret1");
        var secret2 = new SecretVersion("2", SecretVersionState.CURRENT, "secret2");

        var key1 = new Key(vault1, SecretName.of("secret1"));
        var key2 = new Key(vault2, SecretName.of("secret2"));

        tester.put(key1, secret1);
        tester.put(key2, secret2);

        try (var reader = secretReader()){
            reader.getSecret(key1);
            reader.getSecret(key2);

            assertEquals(2, tester.clients().size());
        }
        assertTrue(tester.clients().stream().allMatch(c -> c.isClosed));
    }

    @Test
    void it_returns_secret_with_given_version() {
        var vault = VaultName.of("vault1");
        var key = new Key(vault, SecretName.of("secret1"));

        var expected1 = new SecretVersion("1", SecretVersionState.CURRENT, "v1");
        var expected2 = new SecretVersion("2", SecretVersionState.PENDING, "v2");
        tester.put(key, expected1, expected2);

        try (var reader = secretReader()) {
            var retrieved1 = reader.getSecret(key, SecretVersionId.of("1"));
            var retrieved2 = reader.getSecret(key, SecretVersionId.of("2"));

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
        tester.put(key, current, pending);

        try (var store = secretReader()) {
            var retrieved = store.getSecret(key);
            assertSame(current, retrieved);
        }
    }

    @Test
    void it_throws_exception_if_secret_not_found() {
        var vault = VaultName.of("vault1");
        var key = new Key(vault, SecretName.of("secret1"));
        try (var store = secretReader()) {
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
        tester.put(key, v1);

        try (var store = secretReader()) {
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

        tester.put(key, prev, pend, curr);
        try (var store = secretReader()) {
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
        try (var store = secretReader()) {
            var versions = store.listSecretVersions(key);
            assertEquals(0, versions.size());
        }
    }

    private void assertSame(SecretVersion version, Secret secret) {
        assertEquals(version.value(), secret.secretAsString());
        assertEquals(version.version(), secret.version().value());
    }

}
