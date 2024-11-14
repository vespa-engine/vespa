package com.yahoo.config.provision;

import java.util.Objects;
import java.util.UUID;

/**
 * Globally unique identifier of a snapshot.
 *
 * @author mpolden
 */
public record SnapshotId(UUID uuid) {

    public SnapshotId {
        Objects.requireNonNull(uuid);
    }

    @Override
    public String toString() {
        return uuid.toString();
    }

    public static SnapshotId of(String value) {
        return new SnapshotId(UUID.fromString(value));
    }

}
