// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployer;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Duration;
import java.time.Instant;
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

    protected ApplicationMaintainer(Deployer deployer, NodeRepository nodeRepository, Duration interval) {
        super(nodeRepository, interval);
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

    /** Returns whether given application should be deployed at this moment in time */
    protected boolean canDeployNow(ApplicationId application) {
        return true;
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
        try (MaintenanceDeployment deployment = new MaintenanceDeployment(application, deployer, nodeRepository())) {
            if ( ! deployment.isValid()) return; // this will be done at another config server
            if ( ! canDeployNow(application)) return; // redeployment is no longer needed
            deployment.activate();
        } finally {
            pendingDeployments.remove(application);
        }
    }

    /** Returns the last time application was deployed. Epoch is returned if the application has never been deployed. */
    protected final Instant getLastDeployTime(ApplicationId application) {
        return deployer.lastDeployTime(application).orElse(Instant.EPOCH);
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
