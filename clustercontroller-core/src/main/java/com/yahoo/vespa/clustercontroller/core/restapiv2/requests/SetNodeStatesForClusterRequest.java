// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2.requests;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.state.*;
import com.yahoo.vespa.clustercontroller.core.RemoteClusterControllerTask;
import com.yahoo.vespa.clustercontroller.core.restapiv2.Id;
import com.yahoo.vespa.clustercontroller.core.restapiv2.Request;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InternalFailure;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InvalidContentException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.StateRestApiException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.SetResponse;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitState;

import java.util.Map;
import java.util.logging.Logger;

public class SetNodeStatesForClusterRequest extends Request<SetResponse> {
    private static final Logger log = Logger.getLogger(SetNodeStateRequest.class.getName());

    private final Id.Cluster cluster;
    private final Map<String, UnitState> newStates;
    private final SetUnitStateRequest.Condition condition;


    public SetNodeStatesForClusterRequest(Id.Cluster cluster, SetUnitStateRequest request) {
        super(MasterState.MUST_BE_MASTER);
        this.cluster = cluster;
        this.newStates = request.getNewState();
        this.condition = request.getCondition();
    }

    @Override
    public SetResponse calculateResult(RemoteClusterControllerTask.Context context) throws StateRestApiException {
        if (condition != SetUnitStateRequest.Condition.FORCE) {
            // Setting all nodes to e.g. maintainence is by design unsafe in the sense
            // that it allows effective redundancy to drop to 0, many/all nodes may
            // go down, etc. This is prohibited in Condition.SAFE.
            throw new InvalidContentException(
                    "Setting all nodes in a cluster to a state is only supported with FORCE");
        }

        // Q: What about topology changes after the cluster has been set to maintenance?
        // A: It's not safe to remove nodes when the cluster (the nodes in a cluster)
        // is set to maintenance since all redistribution is shut down. Data may be lost.
        //     New nodes will currently come up with no wanted state, and so will eventually
        // come up. This is a bug with the current implementation - they should automatically
        // be in maintenance.
        //     When suspending an application through the Orchestrator in hosted Vespa, a cluster
        // is set to maintenance to allow nodes to be taken down and up at will. The concern
        // is that doing so will cause lots of redistribution work, and so all nodes in
        // the content clusters are set to maintenance using this call. When new nodes
        // are added while the cluster is set in maintenance, as long as the new nodes
        // do not start with data and there's no feeding there's no redistribution either.
        // So adding new nodes is actually OK in this case.

        for (ConfiguredNode configuredNode : context.cluster.getConfiguredNodes().values()) {
            Node node = new Node(NodeType.STORAGE, configuredNode.index());
            SetResponse setResponse = SetNodeStateRequest.setWantedState(
                    context.cluster,
                    condition,
                    newStates,
                    node,
                    context.nodeStateOrHostInfoChangeHandler,
                    context.currentConsolidatedState);

            if (!setResponse.getWasModified()) {
                throw new InternalFailure("We have not yet implemented the meaning of " +
                        "failing to set the wanted state for a subset of nodes: " +
                        "condition = " + condition +
                        ", newStates = " + newStates +
                        ", currentConsolidatedState = " + context.currentConsolidatedState);
            }
        }

        // 'true' here means the current state now equals the request's wanted state.
        return new SetResponse("ok", true);
    }
}
