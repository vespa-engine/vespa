// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;

import java.util.Optional;

/**
 * The API of anything that can behave essentially like a node.
 *
 * @author bratseth
 */
public interface Nodelike {

    NodeResources resources();

    /** Returns the hostname of the parent if this is a child node */
    Optional<String> parentHostname();

    NodeType type();

}
