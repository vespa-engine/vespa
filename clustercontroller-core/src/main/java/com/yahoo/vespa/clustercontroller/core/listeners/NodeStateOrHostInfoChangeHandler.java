// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.listeners;

import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;

/**
 * Implemented by classes wanting events when node states changes.
 */
public interface NodeStateOrHostInfoChangeHandler {

    void handleNewNodeState(NodeInfo currentInfo, NodeState newState);
    void handleNewWantedNodeState(NodeInfo node, NodeState newState);

    /**
     * For every getnodestate RPC call, handleUpdatedHostInfo() will be called with the host info JSON string.
     */
    void handleUpdatedHostInfo(NodeInfo node, HostInfo newHostInfo);

}
