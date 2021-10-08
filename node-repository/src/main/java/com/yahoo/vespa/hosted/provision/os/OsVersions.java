// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

import java.time.Duration;
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

    /** The maximum number of concurrent upgrades per node type triggered by {@link DelegatingOsUpgrader} */
    private static final int MAX_DELEGATED_UPGRADES = 30;

    private final NodeRepository nodeRepository;
    private final CuratorDatabaseClient db;
    private final boolean reprovisionToUpgradeOs;
    private final int maxDelegatedUpgrades;

    public OsVersions(NodeRepository nodeRepository) {
        this(nodeRepository, nodeRepository.zone().getCloud().reprovisionToUpgradeOs(), MAX_DELEGATED_UPGRADES);
    }

    OsVersions(NodeRepository nodeRepository, boolean reprovisionToUpgradeOs, int maxDelegatedUpgrades) {
        this.nodeRepository = Objects.requireNonNull(nodeRepository);
        this.db = nodeRepository.database();
        this.reprovisionToUpgradeOs = reprovisionToUpgradeOs;
        this.maxDelegatedUpgrades = maxDelegatedUpgrades;

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
            chooseUpgrader(nodeType, target).disableUpgrade(nodeType);
            return change.withoutTarget(nodeType);
        });
    }

    /** Set the target OS version and upgrade budget for nodes of given type */
    public void setTarget(NodeType nodeType, Version version, Duration upgradeBudget, boolean force) {
        require(nodeType);
        requireNonEmpty(version);
        writeChange((change) -> {
            Optional<OsVersionTarget> currentTarget = Optional.ofNullable(change.targets().get(nodeType));
            Optional<Version> currentVersion = currentTarget.map(OsVersionTarget::version);
            Optional<Duration> currentBudget = currentTarget.map(OsVersionTarget::upgradeBudget);

            if (currentVersion.equals(Optional.of(version)) && currentBudget.equals(Optional.of(upgradeBudget))) {
                return change; // Version and upgrade budget are unchanged: Nothing to do
            }

            if (!force && currentVersion.filter(v -> v.isAfter(version)).isPresent()) {
                throw new IllegalArgumentException("Cannot set target OS version to " + version.toFullString() +
                                                   " without setting 'force', as it's lower than the current version: "
                                                   + currentTarget.get().version().toFullString());
            }

            log.info("Set OS target version for " + nodeType + " nodes to " + version.toFullString() +
                     ", with time budget " + upgradeBudget);
            return change.withTarget(version, nodeType, upgradeBudget);
        });
    }

    /** Resume or halt upgrade of given node type */
    public void resumeUpgradeOf(NodeType nodeType, boolean resume) {
        require(nodeType);
        try (Lock lock = db.lockOsVersionChange()) {
            OsVersionTarget target = readChange().targets().get(nodeType);
            if (target == null) return; // No target set for this type
            OsUpgrader upgrader = chooseUpgrader(nodeType, target.version());
            if (resume) {
                upgrader.upgradeTo(target);
            } else {
                upgrader.disableUpgrade(nodeType);
            }
        }
    }

    /** Returns the upgrader to use when upgrading given node type to target */
    private OsUpgrader chooseUpgrader(NodeType nodeType, Version target) {
        if (reprovisionToUpgradeOs) {
            return new RetiringOsUpgrader(nodeRepository);
        }
        // Require rebuild if we have any nodes of this type on a major version lower than target
        boolean rebuildRequired = nodeRepository.nodes().list(Node.State.active).nodeType(nodeType).stream()
                                                .map(Node::status)
                                                .map(Status::osVersion)
                                                .anyMatch(osVersion -> osVersion.current().isPresent() &&
                                                                       osVersion.current().get().getMajor() < target.getMajor());
        if (rebuildRequired) {
            return new RebuildingOsUpgrader(nodeRepository);
        }
        return new DelegatingOsUpgrader(nodeRepository, maxDelegatedUpgrades);
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
