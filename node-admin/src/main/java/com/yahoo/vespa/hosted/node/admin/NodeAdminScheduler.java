// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin;

import com.yahoo.component.AbstractComponent;
import com.yahoo.log.LogLevel;
import com.yahoo.nodeadmin.docker.DockerConfig;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.docker.Docker;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImpl;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepositoryImpl;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.orchestrator.OrchestratorImpl;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author stiankri
 */
public class NodeAdminScheduler extends AbstractComponent {
    private static final Logger log = Logger.getLogger(NodeAdminScheduler.class.getName());

    private static final long INITIAL_DELAY_SECONDS = 0;
    private static final long INTERVAL_IN_SECONDS = 60;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ScheduledFuture<?> scheduledFuture;

    private enum State { WAIT, WORK, STOP }

    private final Object monitor = new Object();
    @GuardedBy("monitor")
    private State state = State.WAIT;
    @GuardedBy("monitor")
    private List<ContainerNodeSpec> wantedContainerState = null;

    public NodeAdminScheduler(final DockerConfig dockerConfig) {
        final Docker docker = new DockerImpl(DockerImpl.newDockerClientFromConfig(dockerConfig));
        final NodeRepository nodeRepository = new NodeRepositoryImpl();
        final Orchestrator orchestrator = new OrchestratorImpl(OrchestratorImpl.makeOrchestratorHostApiClient());
        final Function<HostName, NodeAgent> nodeAgentFactory = (hostName) ->
                new NodeAgentImpl(hostName, docker, nodeRepository, orchestrator);
        final NodeAdmin nodeAdmin = new NodeAdmin(docker, nodeAgentFactory);
        scheduledFuture = scheduler.scheduleWithFixedDelay(
                throwableLoggingRunnable(fetchContainersToRunFromNodeRepository(nodeRepository)),
                INITIAL_DELAY_SECONDS, INTERVAL_IN_SECONDS, SECONDS);
        new Thread(maintainWantedStateRunnable(nodeAdmin), "Node Admin Scheduler main thread").start();
    }

    private void notifyWorkToDo(final Runnable codeToExecuteInCriticalSection) {
        synchronized (monitor) {
            if (state == State.STOP) {
                return;
            }
            state = State.WORK;
            codeToExecuteInCriticalSection.run();
            monitor.notifyAll();
        }
    }

    /**
     * Prevents exceptions from leaking out and suppressing the scheduler from running the task again.
     */
    private static Runnable throwableLoggingRunnable(final Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable throwable) {
                log.log(LogLevel.ERROR, "Unhandled exception leaked out to scheduler.", throwable);
            }
        };
    }

    private Runnable fetchContainersToRunFromNodeRepository(final NodeRepository nodeRepository) {
        return () -> {
            // TODO: should the result from the config server contain both active and inactive?
            final List<ContainerNodeSpec> containersToRun;
            try {
                containersToRun = nodeRepository.getContainersToRun();
            } catch (IOException e) {
                log.log(Level.WARNING, "Failed fetching container info from node repository", e);
                return;
            }
            setWantedContainerState(containersToRun);
        };
    }

    private void setWantedContainerState(final List<ContainerNodeSpec> wantedContainerState) {
        if (wantedContainerState == null) {
            throw new IllegalArgumentException("wantedContainerState must not be null");
        }

        final Runnable codeToExecuteInCriticalSection = () -> this.wantedContainerState = wantedContainerState;
        notifyWorkToDo(codeToExecuteInCriticalSection);
    }

    private Runnable maintainWantedStateRunnable(final NodeAdmin nodeAdmin) {
        return () -> {
            while (true) {
                final List<ContainerNodeSpec> containersToRun;

                synchronized (monitor) {
                    while (state == State.WAIT) {
                        try {
                            monitor.wait();
                        } catch (InterruptedException e) {
                            // Ignore, properly handled by next loop iteration.
                        }
                    }
                    if (state == State.STOP) {
                        return;
                    }
                    assert state == State.WORK;
                    assert wantedContainerState != null;
                    containersToRun = wantedContainerState;
                    state = State.WAIT;
                }

                throwableLoggingRunnable(() -> nodeAdmin.maintainWantedState(containersToRun))
                        .run();
            }
        };
    }

    @Override
    public void deconstruct() {
        scheduledFuture.cancel(false);
        scheduler.shutdown();
        synchronized (monitor) {
            state = State.STOP;
            monitor.notifyAll();
        }
    }
}
