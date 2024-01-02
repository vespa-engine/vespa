// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.flags.IntFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Interface for an OS upgrader. Instances of this are created on-demand because multiple implementations may be used
 * within a single zone. This and subclasses should not have any state.
 *
 * @author mpolden
 */
public abstract class OsUpgrader {

    private final Logger LOG = Logger.getLogger(OsUpgrader.class.getName());

    private final IntFlag maxActiveUpgrades;

    final NodeRepository nodeRepository;

    public OsUpgrader(NodeRepository nodeRepository) {
        this.nodeRepository = Objects.requireNonNull(nodeRepository);
        this.maxActiveUpgrades = PermanentFlags.MAX_OS_UPGRADES.bindTo(nodeRepository.flagSource());
    }

    /** Trigger upgrade to given target */
    abstract void upgradeTo(OsVersionTarget target);

    /** Disable OS upgrade for all nodes of given type */
    abstract void disableUpgrade(NodeType type);

    /** Returns the number of upgrade slots available for given target */
    final int upgradeSlots(OsVersionTarget target, NodeList candidates) {
        if (!candidates.stream().allMatch(node -> node.type() == target.nodeType())) {
            throw new IllegalArgumentException("All node types must type of OS version target " + target.nodeType());
        }
        int max = target.nodeType() == NodeType.host ? maxActiveUpgrades.value() : 1;
        int upgrading = candidates.changingOsVersionTo(target.version()).size();
        return Math.max(0, max - upgrading);
    }

    /** Returns whether node can upgrade to version at given instant */
    final boolean canUpgradeTo(Version version, Instant instant, Node node) {
        Set<Version> versions = nodeRepository.osVersions().availableTo(node, version);
        boolean versionAvailable = versions.contains(version);
        if (!versionAvailable) {
            LOG.log(Level.WARNING, "Want to upgrade host " + node.hostname() + " to OS version " +
                                   version.toFullString() + ", but this version does not exist in " +
                                   node.cloudAccount() + ". Found " + versions.stream().sorted().toList());
        }
        return versionAvailable &&
               (node.status().osVersion().downgrading() || // Fast-track downgrades
                node.history().age(instant).compareTo(gracePeriod()) > 0);
    }

    /** The duration this leaves new nodes alone before scheduling any upgrade */
    private Duration gracePeriod() {
        return nodeRepository.zone().system().isCd() ? Duration.ofHours(4) : Duration.ofDays(1);
    }

}
