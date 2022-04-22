// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.listeners;

import com.yahoo.vespa.clustercontroller.core.NodeInfo;

/**
 * Implemented by classes that wants to be notified of Slobrok events.
 */
public interface SlobrokListener {
    void handleNewNode(NodeInfo node);
    void handleMissingNode(NodeInfo node);
    void handleNewRpcAddress(NodeInfo node);
    void handleReturnedRpcAddress(NodeInfo node);
}
