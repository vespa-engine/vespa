// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;

/**
 * Interface for an OS upgrader.
 *
 * @author mpolden
 */
public interface Upgrader {

    /** Trigger upgrade of nodes of given type */
    void upgrade(NodeType type, Version version);

    /** Disable OS upgrade for all nodes of given type */
    void disableUpgrade(NodeType type);

}
