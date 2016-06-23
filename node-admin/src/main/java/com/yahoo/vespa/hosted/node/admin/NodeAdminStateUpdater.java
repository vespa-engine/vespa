// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin;

import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.node.admin.NodeAdminStateUpdater.State.RESUMED;
import static com.yahoo.vespa.hosted.node.admin.NodeAdminStateUpdater.State.SUSPENDED;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Pulls information from node repository and forwards containers to run to node admin.
 *
 * @author dybis, stiankri
 */
public class NodeAdminStateUpdater extends AbstractComponent {
    private static final Logger log = Logger.getLogger(NodeAdminStateUpdater.class.getName());

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final NodeAdmin nodeAdmin;
    private boolean isRunningUpdates = true;
    private final Object monitor = new Object();
    final Orchestrator orchestrator;
    private final String baseHostName;

    public NodeAdminStateUpdater(
            final NodeRepository nodeRepository,
            final NodeAdmin nodeAdmin,
            long initialSchedulerDelayMillis,
            long intervalSchedulerInMillis,
            Orchestrator orchestrator,
            String baseHostName) {
        scheduler.scheduleWithFixedDelay(
                ()-> fetchContainersToRunFromNodeRepository(nodeRepository),
                initialSchedulerDelayMillis,
                intervalSchedulerInMillis,
                MILLISECONDS);
        this.nodeAdmin = nodeAdmin;
        this.orchestrator = orchestrator;
        this.baseHostName = baseHostName;
    }

    public String getDebugPage() {
        StringBuilder info = new StringBuilder();
        synchronized (monitor) {
            info.append("isRunningUpdates is " + isRunningUpdates+ ". ");
            info.append("NodeAdmin: ");
            info.append(nodeAdmin.debugInfo());
        }
        return info.toString();
    }

    public enum State { RESUMED, SUSPENDED}

    /**
     * @return empty on success and failure message on failure.
     */
    public Optional<String> setResumeStateAndCheckIfResumed(State wantedState) {
        synchronized (monitor) {
            isRunningUpdates = wantedState == RESUMED;
        }
        if (wantedState == SUSPENDED) {
            if (! nodeAdmin.freezeAndCheckIfAllFrozen()) {
                return Optional.of("Not all node agents are frozen.");
            }
        } else {
            nodeAdmin.unfreeze();
        }

        List<String> hosts = new ArrayList<>();
        nodeAdmin.getListOfHosts().forEach(host -> hosts.add(host.toString()));
        if (wantedState == RESUMED) {
            return orchestrator.resume(hosts);
        }
        return orchestrator.suspend(baseHostName, hosts);
    }

    private void fetchContainersToRunFromNodeRepository(final NodeRepository nodeRepository) {
        synchronized (monitor) {
            if (! isRunningUpdates) {
                log.log(Level.FINE, "Is frozen, skipping");
                return;
            }
            final List<ContainerNodeSpec> containersToRun;
            try {
                containersToRun = nodeRepository.getContainersToRun();
            } catch (Throwable t) {
                log.log(Level.WARNING, "Failed fetching container info from node repository", t);
                return;
            }
            if (containersToRun == null) {
                log.log(Level.WARNING, "Got null from NodeRepo.");
                return;
            }
            try {
                nodeAdmin.refreshContainersToRun(containersToRun);
            } catch (Throwable t) {
                log.log(Level.WARNING, "Failed updating node admin: ", t);
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
    }
}
