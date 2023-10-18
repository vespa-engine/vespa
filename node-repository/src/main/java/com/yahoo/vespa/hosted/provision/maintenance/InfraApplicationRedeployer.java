package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.InfraDeployer;
import com.yahoo.config.provision.NodeType;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.applicationmodel.InfrastructureApplication;
import com.yahoo.vespa.hosted.provision.Node.State;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.service.duper.TenantHostApplication;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * Performs on-demand redeployment of the {@link InfrastructureApplication}s, to minimise time between
 * host provisioning for a deployment completing, and deployment of its application containers succeeding.
 *
 * @author jonmv
 */
public class InfraApplicationRedeployer extends AbstractComponent {

    private static final Logger log = Logger.getLogger(InfraApplicationRedeployer.class.getName());

    private final ExecutorService executor = Executors.newSingleThreadExecutor(new DaemonThreadFactory("infra-application-redeployer-"));
    private final Set<InfrastructureApplication> readiedTypes = new ConcurrentSkipListSet<>();
    private final InfraDeployer deployer;
    private final Function<ApplicationId, Mutex> locks;
    private final Supplier<NodeList> nodes;

    @Inject
    public InfraApplicationRedeployer(InfraDeployer deployer, NodeRepository nodes) {
        this(deployer, nodes.applications()::lockMaintenance, nodes.nodes()::list);
    }

    InfraApplicationRedeployer(InfraDeployer deployer, Function<ApplicationId, Mutex> locks, Supplier<NodeList> nodes) {
        this.deployer = deployer;
        this.locks = locks;
        this.nodes = nodes;
    }

    public void readied(NodeType type) {
        readied(applicationOf(type));
    }

    private void readied(InfrastructureApplication application) {
        if (application == null) return;
        if (readiedTypes.add(application)) executor.execute(() -> checkAndRedeploy(application));
    }

    private void checkAndRedeploy(InfrastructureApplication application) {
        log.log(INFO, () -> "Checking if " + application.name() + " should be redeployed");
        if ( ! readiedTypes.remove(application)) return;
        log.log(INFO, () -> "Trying to redeploy " + application.id() + " after completing provisioning of " + application.name());
        try (Mutex lock = locks.apply(application.id())) {
            if (application.nodeType().isHost() && nodes.get().state(State.ready).nodeType(application.nodeType()).isEmpty()) return;
            log.log(INFO, () -> "Redeploying " + application.id() + " after completing provisioning of " + application.name());
            try {
                deployer.getDeployment(application.id()).ifPresent(Deployment::activate);
                readied(childOf(application));
            }
            catch (RuntimeException e) {
                log.log(WARNING, "Failed redeploying " + application.id() + ", will be retried by maintainer", e);
            }
        }
        catch (UncheckedTimeoutException collision) {
            readied(application);
        }
    }

    private static InfrastructureApplication applicationOf(NodeType type) {
        return switch (type) {
            case host -> InfrastructureApplication.TENANT_HOST;
            case confighost -> InfrastructureApplication.CONFIG_SERVER_HOST;
            case controllerhost -> InfrastructureApplication.CONTROLLER_HOST;
            case proxyhost -> InfrastructureApplication.PROXY_HOST;
            default -> null;
        };
    }

    private static InfrastructureApplication childOf(InfrastructureApplication application) {
        return switch (application) {
            case CONFIG_SERVER_HOST -> InfrastructureApplication.CONFIG_SERVER;
            case CONTROLLER_HOST -> InfrastructureApplication.CONTROLLER;
            default -> null;
        };
    }

    @Override
    public void deconstruct() {
        executor.shutdown();
        try {
            if (executor.awaitTermination(10, TimeUnit.SECONDS)) return;
            log.log(WARNING, "Redeployer did not shut down within 10 seconds");
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdownNow();
    }

}
