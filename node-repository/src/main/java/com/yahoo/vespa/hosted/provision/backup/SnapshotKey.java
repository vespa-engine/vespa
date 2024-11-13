package com.yahoo.vespa.hosted.provision.backup;

import ai.vespa.secret.model.SecretVersionId;
import com.yahoo.security.SealedSharedKey;

import java.util.Objects;

/**
 * The sealed encryption key for a {@link Snapshot}.
 *
 * @author mpolden
 */
public record SnapshotKey(SealedSharedKey sharedKey, SecretVersionId sealingKeyVersion) {

    public SnapshotKey {
        Objects.requireNonNull(sharedKey);
        Objects.requireNonNull(sealingKeyVersion);
    }

}
