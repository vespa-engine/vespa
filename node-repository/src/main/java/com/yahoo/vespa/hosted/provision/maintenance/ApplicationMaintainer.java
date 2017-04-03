package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Deployment;
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
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * @author bratseth
 */
public abstract class ApplicationMaintainer extends Maintainer {

    private final Deployer deployer;

    private final Executor deploymentExecutor = Executors.newCachedThreadPool();

    protected ApplicationMaintainer(Deployer deployer, NodeRepository nodeRepository, Duration interval) {
        super(nodeRepository, interval);
        this.deployer = deployer;
    }

    @Override
    protected void maintain() {
        Set<ApplicationId> applications = applicationsNeedingMaintenance();
        for (ApplicationId application : applications) {
            try {
                // An application might change it's state between the time the set of applications is retrieved and the
                // time deployment happens. Lock on application and check if it's still active.
                //
                // Lock is acquired with a low timeout to reduce the chance of colliding with an external deployment.
                try (Mutex lock = nodeRepository().lock(application, Duration.ofSeconds(1))) {
                    if ( ! isActive(application)) continue; // became inactive since we started the loop
                    Optional<Deployment> deployment = deployer.deployFromLocalActive(application, Duration.ofMinutes(30));
                    if ( ! deployment.isPresent()) continue; // this will be done at another config server

                    deploy(application, deployment.get());
                }
                throttle(applications.size());
            }
            catch (RuntimeException e) {
                log.log(Level.WARNING, "Exception on maintenance redeploy of " + application, e);
            }
        }
    }

    /**
     * Redeploy this application using the provided deployer.
     * The default implementatyion deploys asynchronously to make sure we do all applications timely 
     * even when deployments are slow.
     */
    protected void deploy(ApplicationId applicationId, Deployment deployment) {
        deploymentExecutor.execute(() -> {
            try {
                deployment.activate();
            }
            catch (RuntimeException e) {
                log.log(Level.WARNING, "Exception on maintenance redeploy", e);
            }
        });
    }

    /** Block in this method until the next application should be maintained */
    protected abstract void throttle(int applicationCount);

    private Set<ApplicationId> applicationsNeedingMaintenance() {
        return nodesNeedingMaintenance().stream()
                .map(node -> node.allocation().get().owner())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 
     * Returns the nodes whose applications should be maintained by this now. 
     * This should be some subset of the allocated nodes. 
     */
    protected abstract List<Node> nodesNeedingMaintenance();

    private boolean isActive(ApplicationId application) {
        return ! nodeRepository().getNodes(application, Node.State.active).isEmpty();
    }

}
