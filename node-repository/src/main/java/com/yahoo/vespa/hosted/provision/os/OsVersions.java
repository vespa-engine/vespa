// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.provision.NodeRepository;
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

    private final CuratorDatabaseClient db;
    private final OsUpgrader upgrader;

    public OsVersions(NodeRepository nodeRepository) {
        this(nodeRepository, upgraderIn(nodeRepository));
    }

    OsVersions(NodeRepository nodeRepository, OsUpgrader upgrader) {
        this.db = Objects.requireNonNull(nodeRepository).database();
        this.upgrader = Objects.requireNonNull(upgrader);

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
            upgrader.disableUpgrade(nodeType);
            return change.withoutTarget(nodeType);
        });
    }

    /** Set the target OS version and upgrade budget for nodes of given type */
    public void setTarget(NodeType nodeType, Version newTarget, Duration upgradeBudget, boolean force) {
        require(nodeType);
        requireNonZero(newTarget);
        writeChange((change) -> {
            var oldTarget = targetFor(nodeType);
            if (oldTarget.filter(v -> v.equals(newTarget)).isPresent()) {
                return change; // Old target matches new target, nothing to do
            }

            if (!force && oldTarget.filter(v -> v.isAfter(newTarget)).isPresent()) {
                throw new IllegalArgumentException("Cannot set target OS version to " + newTarget.toFullString() +
                                                   " without setting 'force', as it's lower than the current version: "
                                                   + oldTarget.get());
            }

            log.info("Set OS target version for " + nodeType + " nodes to " + newTarget.toFullString());
            return change.withTarget(newTarget, nodeType, upgradeBudget);
        });
    }

    /** Resume or halt upgrade of given node type */
    public void resumeUpgradeOf(NodeType nodeType, boolean resume) {
        require(nodeType);
        try (Lock lock = db.lockOsVersionChange()) {
            var target = readChange().targets().get(nodeType);
            if (target == null) return; // No target set for this type
            if (resume) {
                upgrader.upgradeTo(target);
            } else {
                upgrader.disableUpgrade(nodeType);
            }
        }
    }

    private void requireUpgradeBudget(Optional<Duration> upgradeBudget) {
        if (upgrader instanceof RetiringOsUpgrader && upgradeBudget.isEmpty()) {
            throw new IllegalArgumentException("Zone requires a time budget for OS upgrades");
        }
    }

    private static void requireNonZero(Version version) {
        if (version.isEmpty()) {
            throw new IllegalArgumentException("Invalid target version: " + version.toFullString());
        }
    }

    private static void require(NodeType nodeType) {
        if (!nodeType.isHost()) {
            throw new IllegalArgumentException("Node type '" + nodeType + "' does not support OS upgrades");
        }
    }

    private static OsUpgrader upgraderIn(NodeRepository nodeRepository) {
        if (nodeRepository.zone().getCloud().reprovisionToUpgradeOs()) {
            return new RetiringOsUpgrader(nodeRepository);
        }
        return new DelegatingOsUpgrader(nodeRepository, 30);
    }

}
