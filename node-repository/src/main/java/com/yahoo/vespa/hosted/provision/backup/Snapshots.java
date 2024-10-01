// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.backup;

import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.ClusterId;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDb;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Manage {@link Snapshot}s for nodes in this repository.
 *
 * @author mpolden
 */
public class Snapshots {

    private final NodeRepository nodeRepository;
    private final CuratorDb db;

    public Snapshots(NodeRepository nodeRepository) {
        this.nodeRepository = Objects.requireNonNull(nodeRepository);
        this.db = nodeRepository.database();
    }

    /** Read known backup snapshots, for all hosts */
    public List<Snapshot> read() {
        return db.readSnapshots();
    }

    /** Return backup snapshots for given hostname */
    public List<Snapshot> read(String hostname) {
        return db.readSnapshots(hostname);
    }

    /** Read given snapshot or throw */
    public Snapshot require(String id, String hostname) {
        return read(hostname).stream()
                             .filter(s -> s.id().equals(id))
                             .findFirst()
                             .orElseThrow(() -> new IllegalArgumentException("No snapshot found with ID '" + id + "' and hostname '" + hostname + "'"));
    }

    /** Trigger a new snapshot for node of given hostname */
    public Snapshot create(String hostname, Instant instant) {
        try (var lock = db.lockSnapshots(hostname)) {
            Optional<Snapshot> busySnapshot = read(hostname).stream().filter(s -> s.state().busy()).findFirst();
            if (busySnapshot.isPresent()) {
                throw new IllegalArgumentException("Cannot write snapshot " +
                                                   ": Node " + hostname + " is busy with snapshot " +
                                                   busySnapshot.get().id() + " in state " + busySnapshot.get().state());
            }
            return writeSnapshot(hostname, (node) -> {
                ClusterId cluster = new ClusterId(node.allocation().get().owner(), node.allocation().get().membership().cluster().id());
                return Snapshot.create(com.yahoo.config.provision.HostName.of(hostname), cluster, node.allocation().get().membership().index(), instant);
            }, lock);
        }
    }

    /** Move snapshot to given state */
    public Snapshot move(String snapshotId, String hostname, Snapshot.State newState) {
        try (var lock = db.lockSnapshots(hostname)) {
            Snapshot updated = require(snapshotId, hostname);
            return writeSnapshot(hostname, node -> updated.with(newState), lock);
        }
    }

    private Snapshot writeSnapshot(String hostname, Function<Node, Snapshot> snapshotFunction, @SuppressWarnings("unused") Mutex snapshotLock) {
        List<Snapshot> snapshots = read(hostname);
        try (var nodeMutex = nodeRepository.nodes().lockAndGetRequired(hostname)) {
            Node node = nodeMutex.node();
            if (node.state() != Node.State.active) throw new IllegalArgumentException("Node " + node + " must be " + Node.State.active + ", but is " + node.status());
            if (node.type() != NodeType.tenant) throw new IllegalArgumentException("Node " + node + " has unexpected type " + node.type());
            Optional<ClusterSpec> clusterSpec = node.allocation().map(Allocation::membership).map(ClusterMembership::cluster);
            if (clusterSpec.isEmpty() || clusterSpec.get().type() != ClusterSpec.Type.content) {
                throw new IllegalArgumentException("Node " + node + " is not a member of a content cluster: Refusing to write snapshot");
            }
            Snapshot snapshot = snapshotFunction.apply(node);
            try (var transaction = new NestedTransaction()) {
                Node updatedNode = node.with(node.status().withSnapshot(snapshot));
                db.writeTo(updatedNode.state(),
                           List.of(updatedNode),
                           Agent.system,
                           Optional.empty(),
                           transaction);
                List<Snapshot> updated = new ArrayList<>(snapshots);
                updated.add(snapshot);
                db.writeSnapshots(snapshot.hostname().value(), updated, transaction);
                transaction.commit();
            }
            return snapshot;
        }
    }

}
