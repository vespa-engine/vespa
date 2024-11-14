// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.backup;

import ai.vespa.secret.internal.TypedSecretStore;
import ai.vespa.secret.model.Key;
import ai.vespa.secret.model.Secret;
import ai.vespa.secret.model.SecretVersionId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.SnapshotId;
import com.yahoo.security.KeyId;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SealedSharedKey;
import com.yahoo.security.SecretSharedKey;
import com.yahoo.security.SharedKeyGenerator;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.ClusterId;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDb;
import com.yahoo.vespa.hosted.provision.provisioning.SnapshotStore;

import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;
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
    private final Optional<SnapshotStore> snapshotStore;
    private final TypedSecretStore secretStore;
    private final Optional<String> sealingPrivateKeySecretName;

    public Snapshots(NodeRepository nodeRepository, TypedSecretStore secretStore,
                     Optional<SnapshotStore> snapshotStore,
                     Optional<String> sealingPrivateKeySecretName) {
        this.nodeRepository = Objects.requireNonNull(nodeRepository);
        this.db = nodeRepository.database();
        this.snapshotStore = Objects.requireNonNull(snapshotStore);
        this.secretStore = Objects.requireNonNull(secretStore);
        this.sealingPrivateKeySecretName = Objects.requireNonNull(sealingPrivateKeySecretName);
    }

    /** Read known backup snapshots, for all hosts */
    public List<Snapshot> read() {
        return db.readSnapshots();
    }

    /** Return backup snapshots for given hostname */
    public List<Snapshot> read(String hostname) {
        return db.readSnapshots(hostname);
    }

    /** Lock snapshots for given hostname */
    public Lock lock(String hostname) {
        return db.lockSnapshots(hostname);
    }

    /** Read given snapshot or throw */
    public Snapshot require(SnapshotId id, String hostname) {
        return read(hostname).stream()
                             .filter(s -> s.id().equals(id))
                             .findFirst()
                             .orElseThrow(() -> new IllegalArgumentException("No snapshot found with ID '" + id + "' and hostname '" + hostname + "'"));
    }

    /** Trigger a new snapshot for node of given hostname */
    public Snapshot create(String hostname, Instant instant) {
        VersionedKeyPair keyPair = sealingKeyPair();
        try (var lock = lock(hostname)) {
            SnapshotId id = Snapshot.generateId();
            SecretSharedKey encryptionKey = generateEncryptionKey(keyPair.keyPair(), id);
            return write(id, hostname, (node) -> {
                if (busy(node)) {
                    throw new IllegalArgumentException("Cannot trigger new snapshot: Node " + hostname +
                                                       " is busy with snapshot " + node.status().snapshot().get().id());
                }
                ClusterId cluster = new ClusterId(node.allocation().get().owner(), node.allocation().get().membership().cluster().id());
                return Snapshot.create(id, com.yahoo.config.provision.HostName.of(hostname), node.cloudAccount(),
                                       instant, cluster, node.allocation().get().membership().index(),
                                       new SnapshotKey(encryptionKey.sealedSharedKey(), keyPair.version()));
            }, lock);
        }
    }

    /** Restore a node to given snapshot */
    public Snapshot restore(SnapshotId id, String hostname) {
        try (var lock = lock(hostname)) {
            Snapshot original = require(id, hostname);
            Instant now = nodeRepository.clock().instant();
            return write(id, hostname, (node) -> {
                if (busy(node)) {
                    throw new IllegalArgumentException("Cannot restore snapshot: Node " + hostname +
                                                       " is busy with snapshot " + node.status().snapshot().get().id() + " in " +
                                                       node.status().snapshot().get().state() + " state");
                }
                return original.with(Snapshot.State.restoring, now);
            }, lock);
        }
    }

    /** Returns the encryption key for snapshot, sealed with given public key */
    public SealedSharedKey keyOf(SnapshotId id, String hostname, PublicKey sealingKey) {
        Snapshot snapshot = require(id, hostname);
        return resealKeyOf(snapshot, sealingKey);
    }

    /** Remove given snapshot */
    public void remove(SnapshotId id, String hostname, boolean force) {
        try (var lock = lock(hostname)) {
            List<Snapshot> snapshots = read(hostname);
            Optional<Snapshot> snapshot = snapshots.stream().filter(s -> s.id().equals(id)).findFirst();
            // Delete data if we have a snapshot store available
            if (snapshot.isPresent() && snapshotStore.isPresent()) {
                snapshotStore.get().delete(id, HostName.of(hostname), snapshot.get().cloudAccount());
            }
            Optional<NodeMutex> nodeMutex = nodeRepository.nodes().lockAndGet(hostname);
            try (var transaction = new NestedTransaction()) {
                boolean removingActive = nodeMutex.isPresent() && active(id, nodeMutex.get().node());
                if (removingActive) {
                    Node node = nodeMutex.get().node();
                    if (busyWith(id, node) && !force) {
                        throw new IllegalArgumentException("Cannot remove snapshot " + id +
                                                           ": Node " + hostname + " is working on this snapshot");
                    }
                    Node updatedNode = node.with(node.status().withSnapshot(Optional.empty()));
                    db.writeTo(updatedNode.state(),
                               List.of(updatedNode),
                               Agent.system,
                               Optional.empty(),
                               transaction);
                }
                List<Snapshot> updated = new ArrayList<>(snapshots);
                snapshot.ifPresent(updated::remove);
                db.writeSnapshots(hostname, updated, transaction);
                transaction.commit();
            } finally {
                nodeMutex.ifPresent(NodeMutex::close);
            }
        }
    }

    /** Change state of an active snapshot */
    public Snapshot move(SnapshotId id, String hostname, Snapshot.State newState) {
        try (var lock = lock(hostname)) {
            Snapshot current = require(id, hostname);
            Instant now = nodeRepository.clock().instant();
            return write(id, hostname, node -> {
                if (!busyWith(id, node)) {
                    throw new IllegalArgumentException("Cannot move snapshot " + id + " to " + newState +
                                                       ": Node " + hostname + " is not working on this snapshot");
                }
                return current.with(newState, now);
            }, lock);
        }
    }

    private SecretSharedKey generateEncryptionKey(KeyPair keyPair, SnapshotId id) {
        return SharedKeyGenerator.generateForReceiverPublicKey(keyPair.getPublic(),
                                                               KeyId.ofString(id.toString()));
    }

    /** Reseal the encryption key for snapshot using given public key */
    private SealedSharedKey resealKeyOf(Snapshot snapshot, PublicKey receiverPublicKey) {
        if (snapshot.key().isEmpty()) {
            throw new IllegalArgumentException("Snapshot " + snapshot.id() + " has no encryption key");
        }
        VersionedKeyPair sealingKeyPair = sealingKeyPair(snapshot.key().get().sealingKeyVersion());
        SecretSharedKey unsealedKey = SharedKeyGenerator.fromSealedKey(snapshot.key().get().sharedKey(),
                                                                       sealingKeyPair.keyPair().getPrivate());
        return SharedKeyGenerator.reseal(unsealedKey, receiverPublicKey, KeyId.ofString(snapshot.id().toString()))
                                 .sealedSharedKey();
    }

    /** Returns the key pair to use when sealing the snapshot encryption key */
    private VersionedKeyPair sealingKeyPair(SecretVersionId version) {
        if (sealingPrivateKeySecretName.isEmpty()) {
            throw new IllegalArgumentException("Cannot retrieve sealing key because its secret name is unset");
        }
        Key key = Key.fromString(sealingPrivateKeySecretName.get());
        Secret sealingPrivateKey = version == null ? secretStore.getSecret(key) : secretStore.getSecret(key, version);
        XECPrivateKey privateKey = KeyUtils.fromBase64EncodedX25519PrivateKey(sealingPrivateKey.secretValue().value());
        XECPublicKey publicKey = KeyUtils.extractX25519PublicKey(privateKey);
        return new VersionedKeyPair(new KeyPair(publicKey, privateKey), sealingPrivateKey.version());
    }

    private VersionedKeyPair sealingKeyPair() {
        return sealingKeyPair(null);
    }

    private boolean active(SnapshotId id, Node node) {
        return node.status().snapshot().isPresent() && node.status().snapshot().get().id().equals(id);
    }

    private boolean busy(Node node) {
        return node.status().snapshot().isPresent() && node.status().snapshot().get().state().busy();
    }

    private boolean busyWith(SnapshotId id, Node node) {
        return active(id, node) && busy(node);
    }

    private Snapshot write(SnapshotId id, String hostname, Function<Node, Snapshot> snapshotFunction, @SuppressWarnings("unused") Mutex snapshotLock) {
        List<Snapshot> snapshots = read(hostname);
        try (var nodeMutex = nodeRepository.nodes().lockAndGetRequired(hostname)) {
            Node node = requireNode(nodeMutex);
            Snapshot snapshot = snapshotFunction.apply(node);
            try (var transaction = new NestedTransaction()) {
                Node updatedNode = node.with(node.status().withSnapshot(Optional.of(snapshot)));
                db.writeTo(updatedNode.state(),
                           List.of(updatedNode),
                           Agent.system,
                           Optional.empty(),
                           transaction);
                List<Snapshot> updated = new ArrayList<>(snapshots);
                updated.removeIf(s -> s.id().equals(id));
                updated.add(snapshot);
                db.writeSnapshots(hostname, updated, transaction);
                transaction.commit();
            }
            return snapshot;
        }
    }

    private static Node requireNode(NodeMutex nodeMutex) {
        Node node = nodeMutex.node();
        if (node.state() != Node.State.active) throw new IllegalArgumentException("Node " + node + " must be " + Node.State.active + ", but is " + node.status());
        if (node.type() != NodeType.tenant) throw new IllegalArgumentException("Node " + node + " has unexpected type " + node.type());
        Optional<ClusterSpec> clusterSpec = node.allocation().map(Allocation::membership).map(ClusterMembership::cluster);
        if (clusterSpec.isEmpty() || clusterSpec.get().type() != ClusterSpec.Type.content) {
            throw new IllegalArgumentException("Node " + node + " is not a member of a content cluster: Refusing to write snapshot");
        }
        return node;
    }

    private record VersionedKeyPair(KeyPair keyPair, SecretVersionId version) {}

}
