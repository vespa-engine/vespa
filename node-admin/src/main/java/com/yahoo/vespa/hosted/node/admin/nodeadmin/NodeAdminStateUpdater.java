// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeState;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater.State.SUSPENDED;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Pulls information from node repository and forwards containers to run to node admin.
 *
 * @author dybis, stiankri
 */
public class NodeAdminStateUpdater extends AbstractComponent {
    private final PrefixLogger logger = PrefixLogger.getNodeAdminLogger(NodeAdminStateUpdater.class);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final NodeAdmin nodeAdmin;
    private final Object monitor = new Object();
    private final Orchestrator orchestrator;
    private final String dockerHostHostName;
    private final NodeRepository nodeRepository;

    public NodeAdminStateUpdater(
            final NodeRepository nodeRepository,
            final NodeAdmin nodeAdmin,
            long initialSchedulerDelayMillis,
            long intervalSchedulerInMillis,
            Orchestrator orchestrator,
            String dockerHostHostName) {
        scheduler.scheduleWithFixedDelay(
                ()-> fetchContainersToRunFromNodeRepository(),
                initialSchedulerDelayMillis,
                intervalSchedulerInMillis,
                MILLISECONDS);
        this.nodeAdmin = nodeAdmin;
        this.orchestrator = orchestrator;
        this.dockerHostHostName = dockerHostHostName;
        this.nodeRepository = nodeRepository;
    }

    public Map<String, Object> getDebugPage() {
        Map<String, Object> debug = new LinkedHashMap<>();
        synchronized (monitor) {
            debug.put("dockerHostHostName", dockerHostHostName);
            debug.put("NodeAdmin", nodeAdmin.debugInfo());
        }
        return debug;
    }

    public enum State { RESUMED, SUSPENDED}

    /**
     * This method is used when upgrading the NodeAdmin host. It is exposed through REST-API.
     * @return empty on success and failure message on failure.
     */
    public Optional<String> setResumeStateAndCheckIfResumed(State wantedState) {
        synchronized (monitor) {
            nodeAdmin.setFrozen(wantedState == SUSPENDED);

            if (nodeAdmin.isFrozen()) {
                if (!nodeAdmin.freezeNodeAgentsAndCheckIfAllFrozen()) {
                    return Optional.of("Not all node agents are frozen.");
                }
                // Fetch active nodes from node repo before suspending nodes.
                // It is only possible to suspend active nodes,
                // the orchestrator will fail if trying to suspend nodes in other states.
                // Even though state is frozen we need to interact with node repo, but
                // the data from node repo should not be used for anything else
                List<String> nodesInActiveState;
                try {
                    nodesInActiveState = getNodesInActiveState();
                } catch (IOException e) {
                    return Optional.of("Failed to get nodes from node repo:" + e.getMessage());
                }
                return orchestrator.suspend(dockerHostHostName, nodesInActiveState);
            } else {
                nodeAdmin.unfreezeNodeAgents();
                // we let the NodeAgent do the resume against the orchestrator.
                return Optional.empty();
            }
        }
    }

    /**
     * This is exposed public only for testing.
     */
    public void fetchContainersToRunFromNodeRepository() {
        synchronized (monitor) {
            if (nodeAdmin.isFrozen()) {
                logger.info("Frozen, skipping fetching info from node repository");
                return;
            }
            final List<ContainerNodeSpec> containersToRun;
            try {
                containersToRun = nodeRepository.getContainersToRun();
            } catch (Throwable t) {
                logger.warning("Failed fetching container info from node repository", t);
                return;
            }
            if (containersToRun == null) {
                logger.warning("Got null from node repository");
                return;
            }
            try {
                nodeAdmin.refreshContainersToRun(containersToRun);
            } catch (Throwable t) {
                logger.warning("Failed updating node admin: ", t);
                return;
            }
        }
    }

    @Override
    public void deconstruct() {
        scheduler.shutdown();
        try {
            if (! scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new RuntimeException("Did not manage to shutdown scheduler.");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        nodeAdmin.shutdown();
    }

    private List<String> getNodesInActiveState() throws IOException {
        return nodeRepository.getContainersToRun()
                             .stream()
                             .filter(nodespec -> nodespec.nodeState == NodeState.ACTIVE)
                             .map(nodespec -> nodespec.hostname)
                             .map(HostName::toString)
                             .collect(Collectors.toList());
    }
}
