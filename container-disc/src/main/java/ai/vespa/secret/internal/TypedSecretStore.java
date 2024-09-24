package ai.vespa.secret.internal;

import ai.vespa.secret.model.Key;
import ai.vespa.secret.model.Secret;
import ai.vespa.secret.model.SecretVersionId;
import com.yahoo.container.jdisc.secretstore.SecretStore;

import java.util.List;

public interface TypedSecretStore extends SecretStore {

    enum Type {
        PUBLIC,
        TEST,
        YAHOO
    }

    Secret getSecret(Key key);

    Secret getSecret(Key key, SecretVersionId version);

    /** Lists the existing versions of this secret */
    default List<Secret> listSecretVersions(Key key) {
        throw new UnsupportedOperationException("Secret store does not support listing versions");
    }

    Type type();

    // Do not use! Only for legacy compatibility
    default Secret getSecret(Key k, int i) {
        return getSecret(k, SecretVersionId.of(String.valueOf(i)));
    }

}
