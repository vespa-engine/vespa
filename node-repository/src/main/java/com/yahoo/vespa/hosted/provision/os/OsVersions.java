// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.filter.NodeListFilter;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Thread-safe class that manages target OS versions for nodes in this repository.
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

    /**
     * The maximum number of nodes, within a single node type, that can upgrade in parallel. We limit the number of
     * concurrent upgrades to avoid overloading the orchestrator.
     */
    private static final int MAX_ACTIVE_UPGRADES = 30;

    private final NodeRepository nodeRepository;
    private final CuratorDatabaseClient db;
    private final int maxActiveUpgrades;

    public OsVersions(NodeRepository nodeRepository) {
        this(nodeRepository, MAX_ACTIVE_UPGRADES);
    }

    OsVersions(NodeRepository nodeRepository, int maxActiveUpgrades) {
        this.nodeRepository = Objects.requireNonNull(nodeRepository, "nodeRepository must be non-null");
        this.db = nodeRepository.database();
        this.maxActiveUpgrades = maxActiveUpgrades;

        // Read and write all versions to make sure they are stored in the latest version of the serialized format
        try (var lock = db.lockOsVersions()) {
            db.writeOsVersions(db.readOsVersions());
        }
    }

    /** Returns the current target versions for each node type */
    public Map<NodeType, Version> targets() {
        return db.readOsVersions();
    }

    /** Returns the current target version for given node type, if any */
    public Optional<Version> targetFor(NodeType type) {
        return Optional.ofNullable(targets().get(type));
    }

    /**
     * Remove OS target for given node type. Nodes of this type will stop receiving wanted OS version in their
     * node object.
     */
    public void removeTarget(NodeType nodeType) {
        require(nodeType);
        try (Lock lock = db.lockOsVersions()) {
            var osVersions = db.readOsVersions();
            osVersions.remove(nodeType);
            disableUpgrade(nodeType);
            db.writeOsVersions(osVersions);
        }
    }

    /** Set the target OS version for nodes of given type */
    public void setTarget(NodeType nodeType, Version newTarget, boolean force) {
        require(nodeType);
        if (newTarget.isEmpty()) {
            throw  new IllegalArgumentException("Invalid target version: " + newTarget.toFullString());
        }
        try (Lock lock = db.lockOsVersions()) {
            var osVersions = db.readOsVersions();
            var oldTarget = Optional.ofNullable(osVersions.get(nodeType));

            if (oldTarget.filter(v -> v.equals(newTarget)).isPresent()) {
                return; // Old target matches new target, nothing to do
            }

            if (!force && oldTarget.filter(v -> v.isAfter(newTarget)).isPresent()) {
                throw new IllegalArgumentException("Cannot set target OS version to " + newTarget +
                                                   " without setting 'force', as it's lower than the current version: "
                                                   + oldTarget.get());
            }

            osVersions.put(nodeType, newTarget);
            db.writeOsVersions(osVersions);
            log.info("Set OS target version for " + nodeType + " nodes to " + newTarget.toFullString());
        }
    }

    /** Activate or deactivate upgrade of given node type. This is used for resuming or pausing an OS upgrade. */
    public void setActive(NodeType nodeType, boolean active) {
        require(nodeType);
        try (Lock lock = db.lockOsVersions()) {
            var osVersions = db.readOsVersions();
            var currentVersion = osVersions.get(nodeType);
            if (currentVersion == null) return; // No target version set for this type
            if (active) {
                upgrade(nodeType, currentVersion);
            } else {
                disableUpgrade(nodeType);
            }
        }
    }

    /** Trigger upgrade of nodes of given type*/
    private void upgrade(NodeType type, Version version) {
        var nodes = nodeRepository.list().nodeType(type);
        var numberToUpgrade = Math.max(0, maxActiveUpgrades - nodes.changingOsVersionTo(version).size());
        var nodesToUpgrade = nodes.not().changingOsVersionTo(version)
                                  .not().onOsVersion(version)
                                  .byIncreasingOsVersion()
                                  .first(numberToUpgrade);
        if (nodesToUpgrade.size() == 0) return;
        log.info("Upgrading " + nodesToUpgrade.size() + " nodes of type " + type + " to OS version " + version);
        nodeRepository.upgradeOs(NodeListFilter.from(nodesToUpgrade.asList()), Optional.of(version));
    }

    /** Disable OS upgrade for all nodes of given type */
    private void disableUpgrade(NodeType type) {
        var nodesUpgrading = nodeRepository.list()
                                           .nodeType(type)
                                           .changingOsVersion();
        if (nodesUpgrading.size() == 0) return;
        log.info("Disabling OS upgrade of all " + type + " nodes");
        nodeRepository.upgradeOs(NodeListFilter.from(nodesUpgrading.asList()), Optional.empty());
    }

    private static void require(NodeType nodeType) {
        if (!nodeType.isDockerHost()) {
            throw new IllegalArgumentException("Node type '" + nodeType + "' does not support OS upgrades");
        }
    }

}
