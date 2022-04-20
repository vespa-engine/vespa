// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.listeners;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;

/**
 * Implemented by classes wanting events when there are node changes.
 */
public interface NodeListener {

    default void handleNewNodeState(NodeInfo currentInfo, NodeState newState) {}
    default void handleNewWantedNodeState(NodeInfo node, NodeState newState) {}

    /** Invoked after NodeInfo has been removed from the content cluster. */
    default void handleRemovedNode(Node node) {}

    /**
     * For every getnodestate RPC call, handleUpdatedHostInfo() will be called with the host info JSON string.
     */
    default void handleUpdatedHostInfo(NodeInfo node, HostInfo newHostInfo) {}

}
