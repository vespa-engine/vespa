package com.yahoo.container.jdisc.secret;

import com.yahoo.container.jdisc.secretstore.SecretStore;

import java.util.List;

public interface TypedSecretStore extends SecretStore {

    Secret getSecret(Key key);

    Secret getSecret(Key key, int version);

    /** Lists the existing versions of this secret (nonnegative integers) */
    default List<Secret> listSecretVersions(Key key) {
        throw new UnsupportedOperationException("Secret store does not support listing versions");
    }

}
