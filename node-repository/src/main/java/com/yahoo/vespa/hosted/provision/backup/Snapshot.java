package com.yahoo.vespa.hosted.provision.backup;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.provision.node.ClusterId;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A backup snapshot of a {@link com.yahoo.vespa.hosted.provision.Node}'s local data. Snapshots can only be created for
 * {@link ClusterSpec.Type#content} nodes.
 *
 * @author mpolden
 */
public record Snapshot(SnapshotId id, HostName hostname, State state, History history, ClusterId cluster,
                       int clusterIndex, CloudAccount cloudAccount) {

    public Snapshot {
        Objects.requireNonNull(id);
        Objects.requireNonNull(state);
        Objects.requireNonNull(hostname);
        Objects.requireNonNull(history);
        Objects.requireNonNull(cluster);
        if (clusterIndex < 0) {
            throw new IllegalArgumentException("clusterIndex cannot be negative, got " + cluster);
        }
    }

    /** Returns how long this snapshot has been idle at given instant */
    public Duration idle(Instant instant) {
        Instant latestEvent = history.events().values().stream()
                                     .max(Comparator.comparing(History.Event::at))
                                     .map(History.Event::at)
                                     .get();
        return Duration.between(latestEvent, instant);
    }

    public Snapshot with(State state, Instant at) {
        if (!canChangeTo(state)) {
            throw new IllegalArgumentException("Cannot change state of " + this + " to " + state);
        }
        return new Snapshot(id, hostname, state, history.with(state, at), cluster, clusterIndex, cloudAccount);
    }

    private boolean canChangeTo(State state) {
        // Allow repeated restores
        if (state == State.restoring && this.state == State.restored) return true;
        // Otherwise only allow state changes in the order of the state enum, and at most one step at a time
        return state.compareTo(this.state) >= 0 &&
               state.ordinal() - this.state.ordinal() <= 1;
    }

    /** A recording of the most recent time of each state change */
    public record History(ImmutableMap<State, Event> events) {

        public History {
            Objects.requireNonNull(events);
        }

        public Optional<Event> event(State type) {
            return Optional.ofNullable(events.get(type));
        }

        public History with(State type, Instant at) {
            return new History(builderWithout(type).put(type, new Event(type, at)).build());
        }

        private ImmutableMap.Builder<State, Event> builderWithout(State type) {
            ImmutableMap.Builder<State, Event> builder = ImmutableMap.builder();
            events.forEach((t, at) -> {
                if (t != type) {
                    builder.put(t, at);
                }
            });
            return builder;
        }

        public static History of(State type, Instant at) {
            return new History(ImmutableMap.of(type, new Event(type, at)));
        }

        public record Event(State type, Instant at) {

            public Event {
                Objects.requireNonNull(type);
                Objects.requireNonNull(at);
            }

        }

    }

    public enum State {
        /** Snapshot is being created by the node */
        creating,
        /** The node successfully created a snapshot */
        created,
        /** Snapshot is being restored by the node */
        restoring,
        /** The node successfully created a snapshot */
        restored;

        /** Returns whether this state indicates that we're awaiting and action from the node */
        public boolean busy() {
            return switch (this) {
                case creating, restoring -> true;
                case created, restored -> false;
            };
        }

    }

    public static SnapshotId generateId() {
        return new SnapshotId(UUID.randomUUID());
    }

    public static Snapshot create(SnapshotId id, HostName hostname, CloudAccount cloudAccount, Instant at, ClusterId cluster, int clusterIndex) {
        return new Snapshot(id, hostname, State.creating, History.of(State.creating, at), cluster, clusterIndex, cloudAccount);
    }

}
