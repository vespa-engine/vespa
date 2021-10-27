// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

/**
 * All the states a node can be in the node-repository.
 *
 * See com.yahoo.vespa.hosted.provision.NodeState
 *
 * @author freva
 */
public enum NodeState {
    provisioned, ready, reserved, active, inactive, dirty, failed, parked, deprovisioned, breakfixed
}
