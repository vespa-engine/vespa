// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.flags.IntFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Duration;
import java.time.Instant;

/**
 * Interface for an OS upgrader.
 *
 * @author mpolden
 */
public abstract class OsUpgrader {

    private final IntFlag maxActiveUpgrades;

    final NodeRepository nodeRepository;

    public OsUpgrader(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
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

    /** Returns whether node can upgrade at given instant */
    final boolean canUpgradeAt(Instant instant, Node node) {
        return node.history().age(instant).compareTo(gracePeriod()) > 0;
    }

    /** The duration this leaves new nodes alone before scheduling any upgrade */
    private Duration gracePeriod() {
        return Duration.ofDays(1);
    }

}
