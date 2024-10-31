package ai.vespa.secret.model;

import java.util.Objects;

public record Key(VaultName vaultName, SecretName secretName) {

    public Key {
        Objects.requireNonNull(vaultName, "vaultName cannot be null");
        Objects.requireNonNull(secretName, "secretName cannot be null");
    }

    @Override
    public String toString() {
        return vaultName.value() + "/" + secretName.value();
    }

    public static Key fromString(String key) {
        String[] parts = key.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Key must be on the form 'vaultName/secretName'");
        }
        return new Key(VaultName.of(parts[0]), SecretName.of(parts[1]));
    }


    /* Legacy constructor and methods for backwards compatibility */

    public Key(String keyGroup, String keyName) {
        this(VaultName.of(keyGroup), SecretName.of(keyName));
    }

    public String keyGroup() {
        return vaultName.value();
    }

    public String keyName() {
        return secretName.value();
    }

}
