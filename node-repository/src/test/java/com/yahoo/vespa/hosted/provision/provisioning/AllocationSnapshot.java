// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.vespa.hosted.provision.NodeList;

/**
 * @author smorgrav
 */
public class AllocationSnapshot {
    NodeList nodes;
    String message;
    String task;

    AllocationSnapshot(NodeList nodes, String task, String message) {
        this.nodes = nodes;
        this.message = message;
        this.task = task;
    }
}
