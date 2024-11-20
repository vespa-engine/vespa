// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.secret.aws;

import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import ai.vespa.secret.config.SecretsConfig;
import ai.vespa.secret.internal.TypedSecretStore;
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
    public SecretsImpl(SecretsConfig config, AsmSecretReader asmSecretReader) {
        this.secretStore = asmSecretReader;
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

        return new SecretImpl(vaultName, secretName, secretStore);
   }
}
