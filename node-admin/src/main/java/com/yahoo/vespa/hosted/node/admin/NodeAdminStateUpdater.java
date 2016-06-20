// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.docker.Docker;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepositoryImpl;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.orchestrator.OrchestratorImpl;
import com.yahoo.vespa.hosted.node.admin.util.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Pulls information from node repository and forwards containers to run to node admin.
 *
 * @author dybis, stiankri
 */
public class NodeAdminStateUpdater extends AbstractComponent {
    private static final Logger log = Logger.getLogger(NodeAdminStateUpdater.class.getName());

    private static final long INITIAL_SCHEDULER_DELAY_SECONDS = 0;
    private static final long INTERVAL_SCHEDULER_IN_SECONDS = 60;

    private static final int HARDCODED_NODEREPOSITORY_PORT = 19071;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final String ENV_HOSTNAME = "HOSTNAME";
    private final NodeAdmin nodeAdmin;
    private boolean isRunningUpdates = true;
    private final Object monitor = new Object();
    final Orchestrator orchestrator;

    // For testing.
    public NodeAdminStateUpdater(
            final NodeRepository nodeRepository,
            final NodeAdmin nodeAdmin,
            long initialSchedulerDelayMillis,
            long intervalSchedulerInMillis,
            Orchestrator orchestrator) {
        scheduler.scheduleWithFixedDelay(
                ()-> fetchContainersToRunFromNodeRepository(nodeRepository),
                initialSchedulerDelayMillis,
                intervalSchedulerInMillis,
                MILLISECONDS);
        this.nodeAdmin = nodeAdmin;
        this.orchestrator = orchestrator;
    }

    @Inject
    public NodeAdminStateUpdater(final Docker docker) {
        // TODO: This logic does not belong here, NodeAdminScheduler should not build NodeAdmin with all
        // belonging parts.
        String baseHostName = java.util.Optional.ofNullable(System.getenv(ENV_HOSTNAME))
                .orElseThrow(() -> new IllegalStateException("Environment variable " + ENV_HOSTNAME + " unset"));

        final Set<HostName> configServerHosts = Environment.getConfigServerHostsFromYinstSetting();
        if (configServerHosts.isEmpty()) {
            throw new IllegalStateException("Environment setting for config servers missing or empty.");
        }

        final NodeRepository nodeRepository = new NodeRepositoryImpl(configServerHosts, HARDCODED_NODEREPOSITORY_PORT, baseHostName);

        orchestrator = OrchestratorImpl.createOrchestratorFromSettings();
        final Function<HostName, NodeAgent> nodeAgentFactory = (hostName) ->
                new NodeAgentImpl(hostName, docker, nodeRepository, orchestrator);
        final NodeAdmin nodeAdmin = new NodeAdminImpl(docker, nodeAgentFactory);
        scheduler.scheduleWithFixedDelay(()-> fetchContainersToRunFromNodeRepository(nodeRepository),
                INITIAL_SCHEDULER_DELAY_SECONDS, INTERVAL_SCHEDULER_IN_SECONDS, SECONDS);
        this.nodeAdmin = nodeAdmin;
    }

    public Optional<String> setResumeStateAndCheckIfResumed(boolean resume) {
        synchronized (monitor) {
            isRunningUpdates = resume;
        }
        if (! nodeAdmin.setFreezeAndCheckIfAllFrozen(resume)) {
            return Optional.of("Not all node agents in correct state yet.");
        }
        List<String> hosts = new ArrayList<>();
        nodeAdmin.getListOfHosts().forEach(host -> hosts.add(host.toString()));
        if (resume) {
            return orchestrator.resume(hosts);
        }
        return orchestrator.suspend("parenthost", hosts);
    }

    private void fetchContainersToRunFromNodeRepository(final NodeRepository nodeRepository) {
        synchronized (monitor) {
            if (! isRunningUpdates) {
                log.log(Level.FINE, "Is frozen, skipping");
                return;
            }
            // TODO: should the result from the config server contain both active and inactive?
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
                nodeAdmin.setState(containersToRun);
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
