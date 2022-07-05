// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.History;

import java.time.Duration;
import java.time.Instant;

/**
 * Interface for an OS upgrader.
 *
 * @author mpolden
 */
public interface OsUpgrader {

    /** The duration we should leave new nodes along before scheduling OS upgrades */
    Duration gracePeriod = Duration.ofDays(30);

    /** Trigger upgrade to given target */
    void upgradeTo(OsVersionTarget target);

    /** Disable OS upgrade for all nodes of given type */
    void disableUpgrade(NodeType type);

    default boolean shouldUpgrade(Node node, Instant now) {
        return node.history().age(now).toSeconds() > gracePeriod.toSeconds();
    }

}
