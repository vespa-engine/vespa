// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;

import java.time.Instant;

/**
 * Interface for an OS upgrader.
 *
 * @author mpolden
 */
public interface OsUpgrader {

    /** Trigger upgrade to given target */
    void upgradeTo(OsVersionTarget target);

    /** Disable OS upgrade for all nodes of given type */
    void disableUpgrade(NodeType type);

    /** Returns whether node can upgrade at given instant */
    default boolean canUpgradeAt(Instant instant, Node node) {
        return true;
    }

}
