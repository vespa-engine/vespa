package ai.vespa.secret.aws;

import ai.vespa.secret.config.SecretsConfig;
import ai.vespa.secret.internal.TypedSecretStore;
import ai.vespa.secret.model.Key;
import ai.vespa.secret.model.Secret;
import ai.vespa.secret.model.SecretVersionId;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class SecretsImplTest {

    private record SecretConfig(String key, String name, String vault) {}

    private final static Map<String, String> secretsInVault = Map.of(
            "my-api-key", "0123456789",
            "another-api-key", "9876543210"
    );

    private final static List<SecretConfig> secretsConfig = List.of(
            new SecretConfig("myApiKey", "my-api-key", "prod"),
            new SecretConfig("anotherApiKey", "another-api-key", "prod"),
            new SecretConfig("mySecret", "my-secret", "prod")  // not in vault
    );

    private final static SecretsImpl secrets = createSecrets();

    @Test
    public void testSecretsCanBeRetrieved() {
        assertEquals("0123456789", secrets.get("myApiKey").current());
        assertEquals("9876543210", secrets.get("anotherApiKey").current());
    }

    @Test
    public void testThrowOnUnknownSecrets() {
        try {
            secrets.get("unknown");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Secret with key 'unknown' not found in secrets config", e.getMessage());
        }
    }

    @Test
    public void testSecretInConfigButNotInVault() {
        try {
            secrets.get("mySecret");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Secret with key 'mySecret' not found in secret store", e.getMessage());
        }
    }

    private static SecretsImpl createSecrets() {
        var config = createSecretsConfig();
        var secretStore = createSecretStore();
        return new SecretsImpl(config, secretStore);
    }

    private static SecretsConfig createSecretsConfig() {
        SecretsConfig.Builder builder = new SecretsConfig.Builder();
        secretsConfig.forEach(secret ->
            builder.secret(secret.key(), new SecretsConfig.Secret.Builder().name(secret.name()).vault(secret.vault()))
        );
        return builder.build();
    }

    private static TypedSecretStore createSecretStore() {
        var secretStore = new MockSecretStore();
        secretsInVault.forEach((k, v) -> {
            var key = new Key("prod", k);
            var secretValue = v.getBytes();
            var version = new SecretVersionId("1");
            secretStore.putSecret(key, new Secret(key, secretValue, version));
        });
        return secretStore;
    }

    private static class MockSecretStore implements TypedSecretStore {

        private Map<Key, Secret> secrets = new HashMap<>();

        public void putSecret(Key key, Secret secret) {
            secrets.put(key, secret);
        }

        @Override
        public Secret getSecret(Key key, SecretVersionId version) {
            return secrets.get(key);
        }

        @Override
        public Secret getSecret(Key key) {
            return getSecret(key, null);
        }

        @Override
        public Type type() {
            return Type.PUBLIC;
        }

        @Override
        public String getSecret(String key) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public String getSecret(String key, int version) {
            throw new UnsupportedOperationException("Not implemented");
        }
    }


}
