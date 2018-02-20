// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;

/**
 * Remote interface between the fleet controller and controlled nodes.
 */
public interface Communicator {

    int TRANSIENT_ERROR = 9999;

    interface Waiter<V> {
        void done(V reply);
    }

    void propagateOptions(final FleetControllerOptions options);

    void getNodeState(NodeInfo node, Waiter<GetNodeStateRequest> waiter);

    void setSystemState(ClusterStateBundle states, NodeInfo node, Waiter<SetClusterStateRequest> waiter);

    void shutdown();

}
