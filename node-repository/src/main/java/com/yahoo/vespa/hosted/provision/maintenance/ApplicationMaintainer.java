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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * @author bratseth
 * @author mpolden
 */
public abstract class ApplicationMaintainer extends Maintainer {

    private final Deployer deployer;

    // Use a fixed thread pool to avoid overload on config servers. Resource usage when deploying varies
    // a lot between applications, so doing one by one avoids issues where one or more resource-demanding
    // deployments happen simultaneously
    private final Executor deploymentExecutor = Executors.newSingleThreadExecutor(new DaemonThreadFactory("node repo application maintainer"));

    protected ApplicationMaintainer(Deployer deployer, NodeRepository nodeRepository, Duration interval, JobControl jobControl) {
        super(nodeRepository, interval, jobControl);
        this.deployer = deployer;
    }

    @Override
    protected final void maintain() {
        Set<ApplicationId> applications = applicationsNeedingMaintenance();
        for (ApplicationId application : applications) {
            if (canDeployNow(application))
                deploy(application);
        }
    }

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
        deploymentExecutor.execute(() -> deployWithLock(application));
    }

    protected Deployer deployer() { return deployer; }


    protected Set<ApplicationId> applicationsNeedingMaintenance() {
        return nodesNeedingMaintenance().stream()
                .map(node -> node.allocation().get().owner())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Returns the nodes whose applications should be maintained by this now. 
     * This should be some subset of the allocated nodes. 
     */
    protected abstract List<Node> nodesNeedingMaintenance();

    /** Redeploy this application. A lock will be taken for the duration of the deployment activation */
    final void deployWithLock(ApplicationId application) {
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
        }
    }

    /** Returns true when application has at least one active node */
    private boolean isActive(ApplicationId application) {
        return ! nodeRepository().getNodes(application, Node.State.active).isEmpty();
    }

}
