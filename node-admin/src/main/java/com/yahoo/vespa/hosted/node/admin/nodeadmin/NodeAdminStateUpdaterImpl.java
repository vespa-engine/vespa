// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.config.provision.HostName;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.provision.Node;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdaterImpl.State.RESUMED;
import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdaterImpl.State.SUSPENDED_NODE_ADMIN;
import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdaterImpl.State.TRANSITIONING;

/**
 * Pulls information from node repository and forwards containers to run to node admin.
 *
 * @author dybis, stiankri
 */
public class NodeAdminStateUpdaterImpl {
    private static final Logger log = Logger.getLogger(NodeAdminStateUpdaterImpl.class.getName());
    private static final Duration FREEZE_CONVERGENCE_TIMEOUT = Duration.ofMinutes(5);

    private final NodeRepository nodeRepository;
    private final Orchestrator orchestrator;
    private final NodeAdmin nodeAdmin;
    private final String hostHostname;

    public enum State { TRANSITIONING, RESUMED, SUSPENDED_NODE_ADMIN, SUSPENDED }

    private State currentState = SUSPENDED_NODE_ADMIN;

    public NodeAdminStateUpdaterImpl(
            NodeRepository nodeRepository,
            Orchestrator orchestrator,
            NodeAdmin nodeAdmin,
            HostName hostHostname) {
        this.nodeRepository = nodeRepository;
        this.orchestrator = orchestrator;
        this.nodeAdmin = nodeAdmin;
        this.hostHostname = hostHostname.value();
    }

    public void start() {
        nodeAdmin.start();
    }

    public void converge(State wantedState) {
        try {
            convergeState(wantedState);
        } finally {
            if (wantedState != RESUMED && currentState == TRANSITIONING) {
                Duration subsystemFreezeDuration = nodeAdmin.subsystemFreezeDuration();
                if (subsystemFreezeDuration.compareTo(FREEZE_CONVERGENCE_TIMEOUT) > 0) {
                    // We have spent too much time trying to freeze and node admin is still not frozen.
                    // To avoid node agents stalling for too long, we'll force unfrozen ticks now.
                    log.info("Timed out trying to freeze, will force unfreezed ticks");
                    fetchContainersToRunFromNodeRepository();
                    nodeAdmin.setFrozen(false);
                }
            } else if (currentState == RESUMED) {
                fetchContainersToRunFromNodeRepository();
            }
        }
    }

    /**
     * This method attempts to converge node-admin w/agents to a {@link State}
     * with respect to: freeze, Orchestrator, and services running.
     */
    private void convergeState(State wantedState) {
        if (currentState == wantedState) return;
        currentState = TRANSITIONING;

        boolean wantFrozen = wantedState != RESUMED;
        if (!nodeAdmin.setFrozen(wantFrozen)) {
            throw new ConvergenceException("NodeAdmin is not yet " + (wantFrozen ? "frozen" : "unfrozen"));
        }

        boolean hostIsActiveInNR = nodeRepository.getNode(hostHostname).getState() == Node.State.active;
        switch (wantedState) {
            case RESUMED:
                if (hostIsActiveInNR) orchestrator.resume(hostHostname);
                break;
            case SUSPENDED_NODE_ADMIN:
                if (hostIsActiveInNR) orchestrator.suspend(hostHostname);
                break;
            case SUSPENDED:
                // Fetch active nodes from node repo before suspending nodes.
                // It is only possible to suspend active nodes,
                // the orchestrator will fail if trying to suspend nodes in other states.
                // Even though state is frozen we need to interact with node repo, but
                // the data from node repo should not be used for anything else.
                // We should also suspend host's hostname to suspend node-admin
                List<String> nodesInActiveState = getNodesInActiveState();

                List<String> nodesToSuspend = new ArrayList<>(nodesInActiveState);
                if (hostIsActiveInNR) nodesToSuspend.add(hostHostname);
                if (!nodesToSuspend.isEmpty()) {
                    orchestrator.suspend(hostHostname, nodesToSuspend);
                    log.info("Orchestrator allows suspension of " + nodesToSuspend);
                }

                // The node agent services are stopped by this thread, which is OK only
                // because the node agents are frozen (see above).
                nodeAdmin.stopNodeAgentServices(nodesInActiveState);
                break;
            default:
                throw new IllegalStateException("Unknown wanted state " + wantedState);
        }

        log.info("State changed from " + currentState + " to " + wantedState);
        currentState = wantedState;
    }

    private void fetchContainersToRunFromNodeRepository() {
        try {
            final List<NodeSpec> containersToRun = nodeRepository.getNodes(hostHostname);
            nodeAdmin.refreshContainersToRun(containersToRun);
        } catch (Exception e) {
            log.log(LogLevel.WARNING, "Failed to update which containers should be running", e);
        }
    }

    private List<String> getNodesInActiveState() {
        return nodeRepository.getNodes(hostHostname)
                             .stream()
                             .filter(node -> node.getState() == Node.State.active)
                             .map(NodeSpec::getHostname)
                             .collect(Collectors.toList());
    }
}
