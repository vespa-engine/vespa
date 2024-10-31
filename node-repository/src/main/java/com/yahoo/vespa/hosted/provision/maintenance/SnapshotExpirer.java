// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.backup.Snapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Remove stale node {@link Snapshot}s.
 *
 * @author mpolden
 */
public class SnapshotExpirer extends NodeRepositoryMaintainer {

    private static final Duration MIN_IDLE_PERIOD = Duration.ofDays(1);

    public SnapshotExpirer(NodeRepository nodeRepository, Duration interval, Metric metric) {
        super(nodeRepository, interval, metric);
    }

    @Override
    protected double maintain() {
        Map<HostName, List<Snapshot>> snapshotsByHostname = nodeRepository().snapshots().read().stream()
                                                                            .collect(Collectors.groupingBy(Snapshot::hostname));
        NodeList nodes = nodeRepository().nodes().list().nodeType(NodeType.tenant);
        Instant now = nodeRepository().clock().instant();
        snapshotsByHostname.forEach((hostname, snapshots) -> {
            if (!shouldRemoveAny(snapshots, nodes, now)) return;
            try (var lock = nodeRepository().snapshots().lock(hostname.value())) {
                // Re-read and check while holding lock
                snapshots = nodeRepository().snapshots().read(hostname.value());
                for (var snapshot : snapshots) {
                    if (shouldRemove(snapshot, nodes, now)) {
                        remove(snapshot, hostname);
                    }
                }
            }
        });
        return 0;
    }

    private void remove(Snapshot snapshot, HostName hostname) {
        nodeRepository().snapshots().remove(snapshot.id(), hostname.value(), true);
    }

    private boolean shouldRemoveAny(List<Snapshot> snapshots, NodeList nodes, Instant now) {
        return snapshots.stream().anyMatch(s -> shouldRemove(s, nodes, now));
    }

    /** Returns whether given snapshot should be removed */
    private boolean shouldRemove(Snapshot snapshot, NodeList nodes, Instant now) {
        Duration idle = snapshot.idle(now);
        if (idle.compareTo(MIN_IDLE_PERIOD) < 0) return false;              // No:  Snapshot not idle long enough
        // TODO(mpolden): Replace this with a proper policy when implementing application-level backups
        if (nodes.node(snapshot.hostname().value()).isEmpty()) return true; // Yes: Snapshot belongs to non-existent node
        return snapshot.state() == Snapshot.State.restored;                 // Yes: Snapshot has been restored
    }

}
