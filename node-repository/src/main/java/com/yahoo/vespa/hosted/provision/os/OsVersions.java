// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

/**
 * Thread-safe class that manages an OS version change for nodes in this repository. An {@link OsUpgrader} decides how a
 * {@link OsVersionTarget} is applied to nodes.
 *
 * A version target is initially inactive. Activation decision is taken by
 * {@link com.yahoo.vespa.hosted.provision.maintenance.OsUpgradeActivator}.
 *
 * The target OS version for each node type is set through the /nodes/v2/upgrade REST API.
 *
 * @author mpolden
 */
public class OsVersions {

    private static final Logger log = Logger.getLogger(OsVersions.class.getName());

    /** The maximum number of concurrent upgrades per node type */
    private static final int MAX_ACTIVE_UPGRADES = 30;

    private final NodeRepository nodeRepository;
    private final CuratorDatabaseClient db;
    private final int maxActiveUpgrades;
    private final BooleanFlag softRebuildFlag;
    private final Cloud cloud;

    public OsVersions(NodeRepository nodeRepository) {
        this(nodeRepository, nodeRepository.zone().cloud(), MAX_ACTIVE_UPGRADES);
    }

    OsVersions(NodeRepository nodeRepository, Cloud cloud, int maxActiveUpgrades) {
        this.nodeRepository = Objects.requireNonNull(nodeRepository);
        this.db = nodeRepository.database();
        this.maxActiveUpgrades = maxActiveUpgrades;
        this.softRebuildFlag = Flags.SOFT_REBUILD.bindTo(nodeRepository.flagSource());
        this.cloud = Objects.requireNonNull(cloud);

        // Read and write all versions to make sure they are stored in the latest version of the serialized format
        try (var lock = db.lockOsVersionChange()) {
            db.writeOsVersionChange(db.readOsVersionChange());
        }
    }

    /** Returns the current OS version change */
    public OsVersionChange readChange() {
        return db.readOsVersionChange();
    }

    /** Write the current OS version change with the result of the given operation applied */
    public void writeChange(UnaryOperator<OsVersionChange> operation) {
        try (var lock = db.lockOsVersionChange()) {
            OsVersionChange change = readChange();
            OsVersionChange newChange = operation.apply(change);
            if (newChange.equals(change)) return; // Nothing changed
            db.writeOsVersionChange(newChange);
        }
    }

    /** Returns the current target version for given node type, if any */
    public Optional<Version> targetFor(NodeType type) {
        return Optional.ofNullable(readChange().targets().get(type)).map(OsVersionTarget::version);
    }

    /**
     * Remove OS target for given node type. Nodes of this type will stop receiving wanted OS version in their
     * node object.
     */
    public void removeTarget(NodeType nodeType) {
        require(nodeType);
        writeChange((change) -> {
            Version target = Optional.ofNullable(change.targets().get(nodeType))
                                     .map(OsVersionTarget::version)
                                     .orElse(Version.emptyVersion);
            chooseUpgrader(nodeType, Optional.of(target)).disableUpgrade(nodeType);
            return change.withoutTarget(nodeType);
        });
    }

    /** Set the target OS version for nodes of given type */
    public void setTarget(NodeType nodeType, Version version, boolean force) {
        require(nodeType);
        requireNonEmpty(version);
        writeChange((change) -> {
            Optional<OsVersionTarget> currentTarget = Optional.ofNullable(change.targets().get(nodeType));
            Optional<Version> currentVersion = currentTarget.map(OsVersionTarget::version);

            if (currentVersion.equals(Optional.of(version))) {
                return change; // Version unchanged: Nothing to do
            }

            if (!force && currentVersion.filter(v -> v.isAfter(version)).isPresent()) {
                throw new IllegalArgumentException("Cannot set target OS version to " + version.toFullString() +
                                                   " without setting 'force', as it's lower than the current version: "
                                                   + currentTarget.get().version().toFullString());
            }

            log.info("Set OS target version for " + nodeType + " nodes to " + version.toFullString());
            return change.withTarget(version, nodeType);
        });
    }

    /** Resume or halt upgrade of given node type */
    public void resumeUpgradeOf(NodeType nodeType, boolean resume) {
        require(nodeType);
        try (Lock lock = db.lockOsVersionChange()) {
            OsVersionTarget target = readChange().targets().get(nodeType);
            if (target == null) return; // No target set for this type
            OsUpgrader upgrader = chooseUpgrader(nodeType, Optional.of(target.version()));
            if (resume) {
                upgrader.upgradeTo(target);
            } else {
                upgrader.disableUpgrade(nodeType);
            }
        }
    }

    /** Returns whether node can be upgraded now */
    public boolean canUpgrade(Node node) {
        return chooseUpgrader(node.type(), Optional.empty()).canUpgradeAt(nodeRepository.clock().instant(), node);
    }

    /** Returns the upgrader to use when upgrading given node type to target */
    private OsUpgrader chooseUpgrader(NodeType nodeType, Optional<Version> target) {
        if (cloud.dynamicProvisioning()) {
            boolean canSoftRebuild = cloud.name().equals(CloudName.AWS) && softRebuildFlag.value();
            RetiringOsUpgrader retiringOsUpgrader = new RetiringOsUpgrader(nodeRepository, canSoftRebuild, maxActiveUpgrades);
            if (canSoftRebuild) {
                // If soft rebuild is enabled, we can use RebuildingOsUpgrader for hosts with remote storage.
                // RetiringOsUpgrader is then only used for hosts with local storage.
                return new CompositeOsUpgrader(List.of(new RebuildingOsUpgrader(nodeRepository, canSoftRebuild),
                                                       retiringOsUpgrader));
            }
            return retiringOsUpgrader;
        }
        // Require rebuild if we have any nodes of this type on a major version lower than target
        boolean rebuildRequired = target.isPresent() &&
                                  nodeRepository.nodes().list(Node.State.active).nodeType(nodeType).stream()
                                                .map(Node::status)
                                                .map(Status::osVersion)
                                                .anyMatch(osVersion -> osVersion.current().isPresent() &&
                                                                       osVersion.current().get().getMajor() < target.get().getMajor());
        if (rebuildRequired) {
            return new RebuildingOsUpgrader(nodeRepository, false);
        }
        return new DelegatingOsUpgrader(nodeRepository, maxActiveUpgrades);
    }

    private static void requireNonEmpty(Version version) {
        if (version.isEmpty()) {
            throw new IllegalArgumentException("Invalid target version: " + version.toFullString());
        }
    }

    private static void require(NodeType nodeType) {
        if (!nodeType.isHost()) {
            throw new IllegalArgumentException("Node type '" + nodeType + "' does not support OS upgrades");
        }
    }

}
