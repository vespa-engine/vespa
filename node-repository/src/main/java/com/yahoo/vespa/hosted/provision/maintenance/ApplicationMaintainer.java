// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Deployment;
import com.yahoo.log.LogLevel;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author bratseth
 * @author mpolden
 */
public abstract class ApplicationMaintainer extends Maintainer {

    private final Deployer deployer;
    private final CopyOnWriteArrayList<ApplicationId> pendingDeployments = new CopyOnWriteArrayList<>();

    // Use a fixed thread pool to avoid overload on config servers. Resource usage when deploying varies
    // a lot between applications, so doing one by one avoids issues where one or more resource-demanding
    // deployments happen simultaneously
    private final ThreadPoolExecutor deploymentExecutor = new ThreadPoolExecutor(1, 1,
                                                                                 0L, TimeUnit.MILLISECONDS,
                                                                                 new LinkedBlockingQueue<>(),
                                                                                 new DaemonThreadFactory("node repo application maintainer"));

    protected ApplicationMaintainer(Deployer deployer, NodeRepository nodeRepository, Duration interval, JobControl jobControl) {
        super(nodeRepository, interval, jobControl);
        this.deployer = deployer;
    }

    @Override
    protected final void maintain() {
        applicationsNeedingMaintenance().forEach(this::deploy);
    }

    /** Returns the number of deployments that are pending execution */
    public int pendingDeployments() {
        return pendingDeployments.size();
    }

    /**
     * Redeploy this application.
     *
     * The default implementation deploys asynchronously to make sure we do all applications timely
     * even when deployments are slow.
     */
    protected void deploy(ApplicationId application) {
        if (pendingDeployments.addIfAbsent(application)) { // Avoid queuing multiple deployments for same application
            log.log(LogLevel.INFO, application + " will be deployed, last deploy time " +
                                   getLastDeployTime(application));
            deploymentExecutor.execute(() -> deployWithLock(application));
        }
    }

    protected Deployer deployer() { return deployer; }

    /** Returns the applications that should be maintained by this now. */
    protected abstract Set<ApplicationId> applicationsNeedingMaintenance();

    /** Redeploy this application. A lock will be taken for the duration of the deployment activation */
    protected final void deployWithLock(ApplicationId application) {
        // An application might change its state between the time the set of applications is retrieved and the
        // time deployment happens. Lock the application and check if it's still active.
        //
        // Lock is acquired with a low timeout to reduce the chance of colliding with an external deployment.
        try (Mutex lock = nodeRepository().lock(application, Duration.ofSeconds(1))) {
            if ( ! isActive(application)) return; // became inactive since deployment was requested
            Optional<Deployment> deployment = deployer.deployFromLocalActive(application);
            if ( ! deployment.isPresent()) return; // this will be done at another config server
            log.log(LogLevel.DEBUG, this.getClass().getSimpleName() + " deploying " + application);
            deployment.get().activate();
        } catch (RuntimeException e) {
            log.log(LogLevel.WARNING, "Exception on maintenance redeploy", e);
        } finally {
            pendingDeployments.remove(application);
        }
    }

    /** Returns the last time application was deployed. Epoch is returned if the application has never been deployed. */
    protected final Instant getLastDeployTime(ApplicationId application) {
        return deployer.lastDeployTime(application).orElse(Instant.EPOCH);
    }

    /** Returns true when application has at least one active node */
    private boolean isActive(ApplicationId application) {
        return ! nodeRepository().getNodes(application, Node.State.active).isEmpty();
    }

    @Override
    public void deconstruct() {
        super.deconstruct();
        this.deploymentExecutor.shutdownNow();
        try {
            // Give deployments in progress some time to complete
            this.deploymentExecutor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
