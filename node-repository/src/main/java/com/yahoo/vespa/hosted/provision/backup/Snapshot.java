package com.yahoo.vespa.hosted.provision.backup;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.provision.node.ClusterId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A backup snapshot of a node's local data. Only {@link ClusterSpec.Type#content} nodes support snapshots.
 *
 * @author mpolden
 */
public record Snapshot(String id, HostName hostname, State state, Instant createdAt, ClusterId cluster, int clusterIndex) {

    public Snapshot {
        Objects.requireNonNull(id);
        Objects.requireNonNull(state);
        Objects.requireNonNull(hostname);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(cluster);
        if (clusterIndex < 0) {
            throw new IllegalArgumentException("clusterIndex cannot be negative, got " + cluster);
        }
    }

    public Snapshot with(State state) {
        if (state.compareTo(this.state) < 0) {
            throw new IllegalArgumentException("Cannot change state of " + this + " to " + state);
        }
        return new Snapshot(id, hostname, state, createdAt, cluster, clusterIndex);
    }

    public enum State {
        /** Snapshot is being created by the node */
        creating,
        /** The node failed to create a snapshot */
        failed,
        /** The node successfully created a snapshot */
        created,
        /** Snapshot is being restored by the node */
        restoring,
        /** The node failed to restore the snapshot */
        restoreFailed,
        /** The node successfully created a snapshot */
        restored;

        /** Returns whether this state indicates that we're awaiting and action from the node */
        public boolean busy() {
            return switch (this) {
                case creating, restoring -> true;
                case failed, created, restoreFailed, restored -> false;
            };
        }

    }

    public static Snapshot create(HostName hostname, ClusterId cluster, int clusterIndex, Instant at) {
        return new Snapshot(UUID.randomUUID().toString(), hostname, State.creating, at, cluster, clusterIndex);
    }

}
