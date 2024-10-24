package ai.vespa.secret.aws;

import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import ai.vespa.secret.config.SecretsConfig;
import ai.vespa.secret.internal.TypedSecretStore;
import ai.vespa.secret.model.Key;
import ai.vespa.secret.model.SecretName;
import ai.vespa.secret.model.VaultName;

import javax.inject.Inject;

/**
 * Implementation of the {@link Secrets} interface for Vespa cloud.
 *
 * @author lesters
 */
public class SecretsImpl implements Secrets {

    private final SecretsConfig secretsConfig;
    private final TypedSecretStore secretStore;

    @Inject
    public SecretsImpl(SecretsConfig config, AsmSecretStore asmSecretStore) {
        this.secretStore = asmSecretStore;
        this.secretsConfig = config;
    }

    // For testing
    SecretsImpl(SecretsConfig secretsConfig, TypedSecretStore secretStore) {
        this.secretsConfig = secretsConfig;
        this.secretStore = secretStore;
    }

    @Override
    public Secret get(String key) {
        SecretsConfig.Secret secretConfig = secretsConfig.secret(key);
        if (secretConfig == null) {
            throw new IllegalArgumentException("Secret with key '" + key + "' not found in secrets config");
        }

        VaultName vaultName = VaultName.of(secretConfig.vault());
        SecretName secretName = SecretName.of(secretConfig.name());

        var secret = secretStore.getSecret(new Key(vaultName, secretName));
        if (secret == null) {
            throw new IllegalArgumentException("Secret with key '" + key + "' not found in secret store");
        }

        return secret::secretAsString;
    }

}
