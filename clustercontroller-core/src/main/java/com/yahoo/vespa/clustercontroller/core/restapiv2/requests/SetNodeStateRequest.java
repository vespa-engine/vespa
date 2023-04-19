// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2.requests;

import com.yahoo.time.TimeBudget;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.ContentCluster;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;
import com.yahoo.vespa.clustercontroller.core.NodeStateChangeChecker;
import com.yahoo.vespa.clustercontroller.core.RemoteClusterControllerTask;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeListener;
import com.yahoo.vespa.clustercontroller.core.restapiv2.Id;
import com.yahoo.vespa.clustercontroller.core.restapiv2.MissingIdException;
import com.yahoo.vespa.clustercontroller.core.restapiv2.Request;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InvalidContentException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.StateRestApiException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.SetResponse;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitState;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.clustercontroller.core.NodeStateChangeChecker.Result;

public class SetNodeStateRequest extends Request<SetResponse> {
    private static final Logger log = Logger.getLogger(SetNodeStateRequest.class.getName());

    private final Id.Node id;
    private final Map<String, UnitState> newStates;
    private final SetUnitStateRequest.Condition condition;
    private final SetUnitStateRequest.ResponseWait responseWait;
    private final WantedStateSetter wantedState;
    private final TimeBudget timeBudget;
    private final boolean probe;

    public SetNodeStateRequest(Id.Node id, SetUnitStateRequest setUnitStateRequest) {
        this(id, setUnitStateRequest, SetNodeStateRequest::setWantedState);
    }

    /** Public for tests. */
    public SetNodeStateRequest(Id.Node id, SetUnitStateRequest setUnitStateRequest, WantedStateSetter wantedState) {
        super(MasterState.MUST_BE_MASTER);
        this.id = id;
        this.newStates = setUnitStateRequest.getNewState();
        this.condition = setUnitStateRequest.getCondition();
        this.responseWait = setUnitStateRequest.getResponseWait();
        this.wantedState = wantedState;
        this.timeBudget = setUnitStateRequest.timeBudget();
        this.probe = setUnitStateRequest.isProbe();
    }

    @Override
    public SetResponse calculateResult(RemoteClusterControllerTask.Context context) throws StateRestApiException {
        return wantedState.set(
                context.cluster,
                condition,
                newStates,
                id.getNode(),
                context.nodeListener,
                context.currentConsolidatedState,
                context.masterInfo.inMasterMoratorium(),
                probe);
    }

    static NodeState getRequestedNodeState(Map<String, UnitState> newStates, Node n) throws StateRestApiException {
        UnitState newState = newStates.get("user");
        if (newState == null) throw new InvalidContentException("No new user state given in request");
        State state = switch (newState.getId().toLowerCase()) {
            case "up" -> State.UP;
            case "retired" -> State.RETIRED;
            case "maintenance" -> State.MAINTENANCE;
            case "down" -> State.DOWN;
            default -> throw new InvalidContentException("Invalid user state '" + newState.getId() + "' given.");
        };
        return new NodeState(n.getType(), state).setDescription(newState.getReason());
    }

    @Override
    public boolean hasVersionAckDependency() {
        return (responseWait == SetUnitStateRequest.ResponseWait.WAIT_UNTIL_CLUSTER_ACKED);
    }

    @Override
    public Optional<Instant> getDeadline() {
        return timeBudget.deadline();
    }

    @Override
    public boolean isFailed() {
        // Failure to set a node state is propagated as a 200 with wasModified false.
        return super.isFailed() || (resultSet && !result.getWasModified());
    }

    @Override
    public String toString() {
        return "SetNodeStateRequest{" +
               "node=" + id + "," +
               "newState=" + newStates.get("user") + "," +
               (probe ? "probe=" + probe + "," : "") +
               "}";
    }

    static SetResponse setWantedState(
            ContentCluster cluster,
            SetUnitStateRequest.Condition condition,
            Map<String, UnitState> newStates,
            Node node,
            NodeListener stateListener,
            ClusterState currentClusterState,
            boolean inMasterMoratorium,
            boolean probe) throws StateRestApiException {
        if ( ! cluster.hasConfiguredNode(node.getIndex())) {
            throw new MissingIdException(cluster.getName(), node);
        }
        NodeInfo nodeInfo = cluster.getNodeInfo(node);
        if (nodeInfo == null)
            throw new IllegalArgumentException("Cannot set the wanted state of unknown node " + node);

        NodeState wantedState = nodeInfo.getUserWantedState();
        NodeState newWantedState = getRequestedNodeState(newStates, node);
        Result result = cluster.calculateEffectOfNewState(node, currentClusterState, condition, wantedState,
                                                          newWantedState, inMasterMoratorium);

        log.log(Level.FINE, () -> "node=" + node +
                " current-cluster-state=" + currentClusterState + // Includes version in output format
                " condition=" + condition +
                " wanted-state=" + wantedState +
                " new-wanted-state=" + newWantedState +
                " change-check=" + result);

        boolean success = setWantedStateAccordingToResult(
                result,
                newWantedState,
                condition,
                nodeInfo,
                cluster,
                stateListener,
                probe);

        // If the state was successfully set, just return an "ok" message back.
        String reason = success ? "ok" : result.getReason();
        return new SetResponse(reason, success);
    }

    /**
     * Returns true if the current/old wanted state already matches the requested
     * wanted state, or the requested state has been accepted as the new wanted state.
     */
    private static boolean setWantedStateAccordingToResult(
            NodeStateChangeChecker.Result result,
            NodeState newWantedState,
            SetUnitStateRequest.Condition condition,
            NodeInfo nodeInfo,
            ContentCluster cluster,
            NodeListener stateListener,
            boolean probe) {
        if (result.settingWantedStateIsAllowed()) {
            setNewWantedState(nodeInfo, newWantedState, stateListener, probe);
        }

        // True if the wanted state was or has just been set to newWantedState
        boolean success = result.settingWantedStateIsAllowed() || result.wantedStateAlreadySet();

        if (success && condition == SetUnitStateRequest.Condition.SAFE && nodeInfo.isStorage()) {
            // In safe-mode, setting the storage node must be accompanied by changing the state
            // of the distributor. E.g. setting the storage node to maintenance may cause
            // feeding issues unless distributor is also set down.

            setDistributorWantedState(cluster, nodeInfo.getNodeIndex(), newWantedState, stateListener, probe);
        }

        return success;
    }

    /**
     * Set the wanted state on the distributor to something appropriate given the storage is being
     * set to (or is equal to) newStorageWantedState.
     */
    private static void setDistributorWantedState(ContentCluster cluster,
                                                  int index,
                                                  NodeState newStorageWantedState,
                                                  NodeListener stateListener,
                                                  boolean probe) {
        Node distributorNode = new Node(NodeType.DISTRIBUTOR, index);
        NodeInfo nodeInfo = cluster.getNodeInfo(distributorNode);
        if (nodeInfo == null)
            throw new IllegalStateException("Missing distributor at index " + distributorNode.getIndex());

        State newState;
        switch (newStorageWantedState.getState()) {
            case MAINTENANCE -> newState = State.DOWN;
            case RETIRED -> newState = State.UP;
            default -> {
                newState = newStorageWantedState.getState();
                if (!newState.validWantedNodeState(distributorNode.getType()))
                    throw new IllegalStateException("Distributor cannot be set to wanted state " + newState);
            }
        }

        NodeState newWantedState = new NodeState(distributorNode.getType(), newState);
        newWantedState.setDescription(newStorageWantedState.getDescription());

        NodeState currentWantedState = nodeInfo.getUserWantedState();
        if (newWantedState.getState() != currentWantedState.getState() ||
                !Objects.equals(newWantedState.getDescription(),
                        currentWantedState.getDescription())) {
            setNewWantedState(nodeInfo, newWantedState, stateListener, probe);
        }
    }

    private static void setNewWantedState(NodeInfo nodeInfo,
                                          NodeState newWantedState,
                                          NodeListener stateListener,
                                          boolean probe) {
        if (probe) return;
        nodeInfo.setWantedState(newWantedState);
        stateListener.handleNewWantedNodeState(nodeInfo, newWantedState);
    }
}
