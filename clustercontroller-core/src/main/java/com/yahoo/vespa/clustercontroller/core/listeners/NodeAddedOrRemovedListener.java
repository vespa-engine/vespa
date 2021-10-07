// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.listeners;

import com.yahoo.vespa.clustercontroller.core.NodeInfo;

/**
 * Listeners for new nodes detected.
 */
public interface NodeAddedOrRemovedListener {
    void handleNewNode(NodeInfo node);
    void handleMissingNode(NodeInfo node);
    void handleNewRpcAddress(NodeInfo node);
    void handleReturnedRpcAddress(NodeInfo node);
}
