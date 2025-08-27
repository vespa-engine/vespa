// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import ai.vespa.http.DomainName;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Version;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.config.provision.ActivationContext;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InfraDeployer;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.applicationmodel.InfrastructureApplication;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.maintenance.InfrastructureVersions;
import com.yahoo.vespa.service.monitor.DuperModelInfraApi;
import com.yahoo.vespa.service.monitor.InfraApplicationApi;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * Performs on-demand redeployment of the {@link InfrastructureApplication}s, to minimise time between
 * host provisioning for a deployment completing, and deployment of its application containers succeeding.
 *
 * @author freva
 * @author bjorncs
 */
public class InfraDeployerImpl extends AbstractComponent implements InfraDeployer, AutoCloseable {

    private static final Logger logger = Logger.getLogger(InfraDeployerImpl.class.getName());

    private final NodeRepository nodeRepository;
    private final Provisioner provisioner;
    private final InfrastructureVersions infrastructureVersions;
    private final DuperModelInfraApi duperModel;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(new DaemonThreadFactory("infra-application-redeployer-"));
    private final Set<InfrastructureApplication> readiedTypes = new ConcurrentSkipListSet<>();
    private final Function<ApplicationId, Mutex> locks;
    private final Supplier<NodeList> nodes;

    @Inject
    public InfraDeployerImpl(NodeRepository nodeRepository, Provisioner provisioner, DuperModelInfraApi duperModel) {
        this(nodeRepository, provisioner, duperModel, nodeRepository.applications()::lockMaintenance, nodeRepository.nodes()::list);
    }

    InfraDeployerImpl(NodeRepository nodeRepository, Provisioner provisioner, DuperModelInfraApi duperModel,
                      Function<ApplicationId, Mutex> locks, Supplier<NodeList> nodes) {
        this.nodeRepository = nodeRepository;
        this.provisioner = provisioner;
        this.infrastructureVersions = nodeRepository.infrastructureVersions();
        this.duperModel = duperModel;
        this.locks = locks;
        this.nodes = nodes;
    }

    @Override
    public Optional<Deployment> getDeployment(ApplicationId application) {
        return duperModel.getInfraApplication(application).map(InfraDeployment::new);
    }

    @Override
    public void activateAllSupportedInfraApplications(boolean propagateException) {
        duperModel.getSupportedInfraApplications().stream()
                // nodes cannot be activated before their host, so try to activate the host first
                .sorted(Comparator.comparing(n -> !n.getCapacity().type().isHost()))
                .forEach(api -> {
            var application = api.getApplicationId();
            var deployment = new InfraDeployment(api);
            try (var lock = nodeRepository.applications().lockMaintenance(application)) {
                deployment.activate();
            } catch (RuntimeException e) {
                logger.log(Level.INFO, "Failed to activate " + application, e);
                if (propagateException) {
                    throw e;
                }
                // loop around to activate the next application
            }
        });

        duperModel.infraApplicationsIsNowComplete();
    }

    @Override
    public void readied(NodeType type) {
        applicationOf(type).ifPresent(this::readied);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (executor.awaitTermination(10, TimeUnit.SECONDS)) return;
            logger.log(WARNING, "Redeployer did not shut down within 10 seconds");
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdownNow();
    }

    @Override public void deconstruct() { close(); }

    private void readied(InfrastructureApplication application) {
        if (application == null) return;
        if (readiedTypes.add(application)) executor.execute(() -> checkAndRedeploy(application));
    }

    private void checkAndRedeploy(InfrastructureApplication application) {
        if ( ! readiedTypes.remove(application)) return;
        try (Mutex lock = locks.apply(application.id())) {
            if (application.nodeType().isHost() && nodes.get().state(Node.State.ready).nodeType(application.nodeType()).isEmpty()) return;
            logger.log(FINE, () -> "Redeploying " + application.id() + " after completing provisioning for " + application.name());
            try {
                getDeployment(application.id()).ifPresent(Deployment::activate);
                childOf(application).ifPresent(this::readied);
            }
            catch (RuntimeException e) {
                logger.log(INFO, "Failed redeploying " + application.id() + ", will be retried by maintainer", e);
            }
        }
        catch (UncheckedTimeoutException collision) {
            readied(application);
        }
    }

    private static Optional<InfrastructureApplication> applicationOf(NodeType type) {
        return switch (type) {
            case host -> Optional.of(InfrastructureApplication.TENANT_HOST);
            case confighost -> Optional.of(InfrastructureApplication.CONFIG_SERVER_HOST);
            case config -> Optional.of(InfrastructureApplication.CONFIG_SERVER);
            case controllerhost -> Optional.of(InfrastructureApplication.CONTROLLER_HOST);
            case controller -> Optional.of(InfrastructureApplication.CONTROLLER);
            case proxyhost -> Optional.of(InfrastructureApplication.PROXY_HOST);
            default -> Optional.empty();
        };
    }

    private static Optional<InfrastructureApplication> childOf(InfrastructureApplication application) {
        return switch (application) {
            case CONFIG_SERVER_HOST -> Optional.of(InfrastructureApplication.CONFIG_SERVER);
            case CONTROLLER_HOST -> Optional.of(InfrastructureApplication.CONTROLLER);
            default -> Optional.empty();
        };
    }


    private class InfraDeployment implements Deployment {

        private final InfraApplicationApi application;

        private boolean prepared = false;
        private List<HostSpec> hostSpecs;

        private InfraDeployment(InfraApplicationApi application) {
            this.application = application;
        }

        @Override
        public void prepare() {
            if (prepared) return;

            NodeType nodeType = application.getCapacity().type();
            Version targetVersion = infrastructureVersions.getTargetVersionFor(nodeType);
            hostSpecs = provisioner.prepare(application.getApplicationId(),
                                            application.getClusterSpecWithVersion(targetVersion),
                                            application.getCapacity(),
                                            logger::log);

            prepared = true;
        }

        @Override
        public long activate() {
            prepare();
            try (var lock = provisioner.lock(application.getApplicationId())) {
                if (hostSpecs.isEmpty()) {
                    logger.log(Level.FINE, () -> "No nodes to provision for " + application.getCapacity().type() + ", removing application");
                    removeApplication(application.getApplicationId());
                } else {
                    NestedTransaction nestedTransaction = new NestedTransaction();
                    provisioner.activate(hostSpecs, new ActivationContext(0, !application.getCapacity().canFail()), new ApplicationTransaction(lock, nestedTransaction));
                    nestedTransaction.commit();

                    duperModel.infraApplicationActivated(
                            application.getApplicationId(),
                            hostSpecs.stream().map(HostSpec::hostname).map(DomainName::of).toList());

                    logger.log(Level.FINE, () -> generateActivationLogMessage(hostSpecs, application.getApplicationId()));
                }

                return 0; // No application config version here
            }
        }

        @Override
        public void restart(HostFilter filter) {
            provisioner.restart(application.getApplicationId(), filter);
        }
    }

    private void removeApplication(ApplicationId applicationId) {
        // Use the DuperModel as source-of-truth on whether it has also been activated (to avoid periodic removals)
        if (duperModel.infraApplicationIsActive(applicationId)) {
            try (var lock = provisioner.lock(applicationId)) {
                NestedTransaction nestedTransaction = new NestedTransaction();
                provisioner.remove(new ApplicationTransaction(lock, nestedTransaction));
                nestedTransaction.commit();
                duperModel.infraApplicationRemoved(applicationId);
            }
        }
    }

    private static String generateActivationLogMessage(List<HostSpec> hostSpecs, ApplicationId applicationId) {
        String detail;
        if (hostSpecs.size() < 10) {
            detail = ": " + hostSpecs.stream().map(HostSpec::hostname).collect(Collectors.joining(","));
        } else {
            detail = " with " + hostSpecs.size() + " hosts";
        }
        return "Infrastructure application " + applicationId + " activated" + detail;
    }

}
