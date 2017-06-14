// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2.requests;

import com.yahoo.log.LogLevel;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;
import com.yahoo.vespa.clustercontroller.core.NodeStateChangeChecker;
import com.yahoo.vespa.clustercontroller.core.RemoteClusterControllerTask;
import com.yahoo.vespa.clustercontroller.core.ContentCluster;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeAddedOrRemovedListener;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeStateOrHostInfoChangeHandler;
import com.yahoo.vespa.clustercontroller.core.restapiv2.Id;
import com.yahoo.vespa.clustercontroller.core.restapiv2.MissingIdException;
import com.yahoo.vespa.clustercontroller.core.restapiv2.Request;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.*;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.SetResponse;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitState;

import java.util.Map;
import java.util.logging.Logger;

public class SetNodeStateRequest extends Request<SetResponse> {
    private static final Logger log = Logger.getLogger(SetNodeStateRequest.class.getName());

    private final Id.Node id;
    private final Map<String, UnitState> newStates;
    private final SetUnitStateRequest.Condition condition;


    public SetNodeStateRequest(Id.Node id, SetUnitStateRequest setUnitStateRequest) {
        super(MasterState.MUST_BE_MASTER);
        this.id = id;
        this.newStates = setUnitStateRequest.getNewState();
        this.condition = setUnitStateRequest.getCondition();
    }

    @Override
    public SetResponse calculateResult(RemoteClusterControllerTask.Context context) throws StateRestApiException {
        SetResponse setResponse = setWantedState(
                context.cluster,
                condition,
                newStates,
                id.getNode(),
                context.nodeStateOrHostInfoChangeHandler,
                context.currentState);
        return setResponse;
    }

    static NodeState getRequestedNodeState(Map<String, UnitState> newStates, Node n) throws StateRestApiException {
        UnitState newState = newStates.get("user");
        if (newState == null) throw new InvalidContentException("No new user state given in request");
        State state;
        switch (newState.getId().toLowerCase()) {
            case "up": state = State.UP; break;
            case "retired": state = State.RETIRED; break;
            case "maintenance": state = State.MAINTENANCE; break;
            case "down": state = State.DOWN; break;
            default: throw new InvalidContentException("Invalid user state '" + newState.getId() + "' given.");
        }
        return new NodeState(n.getType(), state).setDescription(newState.getReason());
    }

    static SetResponse setWantedState(
            ContentCluster cluster,
            SetUnitStateRequest.Condition condition,
            Map<String, UnitState> newStates,
            Node node,
            NodeStateOrHostInfoChangeHandler stateListener,
            ClusterState currentClusterState) throws StateRestApiException {
        if ( ! cluster.getConfiguredNodes().containsKey(node.getIndex())) {
            throw new MissingIdException(cluster.getName(), node);
        }
        NodeInfo nodeInfo = cluster.getNodeInfo(node);
        if (nodeInfo == null)
            throw new IllegalArgumentException("Cannot set the wanted state of unknown node " + node);

        NodeState wantedState = nodeInfo.getUserWantedState();
        NodeState newWantedState = getRequestedNodeState(newStates, node);
        NodeStateChangeChecker.Result result = cluster.calculateEffectOfNewState(
                node, currentClusterState, condition, wantedState, newWantedState);

        log.log(LogLevel.DEBUG, "node=" + node +
                " current-cluster-state=" + currentClusterState + // Includes version in output format
                " condition=" + condition +
                " wanted-state=" + wantedState +
                " new-wanted-state=" + newWantedState +
                " change-check=" + result);
        if (result.settingWantedStateIsAllowed()) {
            nodeInfo.setWantedState(newWantedState);
            stateListener.handleNewWantedNodeState(nodeInfo, newWantedState);
        }

        // wasModified is true if the new/current State equals the wanted state in the request.
        boolean wasModified = result.settingWantedStateIsAllowed() || result.wantedStateAlreadySet();
        // If the state was successfully set, just return an "ok" message back.
        String reason = wasModified ? "ok" : result.getReason();
        return new SetResponse(reason, wasModified);
    }
}
