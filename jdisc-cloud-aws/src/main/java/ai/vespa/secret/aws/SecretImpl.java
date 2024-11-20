//  Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.secret.aws;

import ai.vespa.secret.Secret;
import ai.vespa.secret.internal.TypedSecretStore;
import ai.vespa.secret.model.Key;
import ai.vespa.secret.model.SecretName;
import ai.vespa.secret.model.VaultName;

/**
 * @author mortent
 */
public class SecretImpl implements Secret {

    private final VaultName vaultName;
    private final SecretName secretName;
    private final TypedSecretStore secrets;

    public SecretImpl(VaultName vaultName, SecretName secretName, TypedSecretStore secrets) {
        this.vaultName = vaultName;
        this.secretName = secretName;
        this.secrets = secrets;
    }

    @Override
    public String current() {
        var secret = secrets.getSecret(new Key(vaultName, secretName));
        return secret.secretAsString();
    }
}
