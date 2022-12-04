// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ActivationContext;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InfraDeployer;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.maintenance.InfrastructureVersions;
import com.yahoo.vespa.service.monitor.DuperModelInfraApi;
import com.yahoo.vespa.service.monitor.InfraApplicationApi;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author freva
 */
public class InfraDeployerImpl implements InfraDeployer {

    private static final Logger logger = Logger.getLogger(InfraDeployerImpl.class.getName());

    private final NodeRepository nodeRepository;
    private final Provisioner provisioner;
    private final InfrastructureVersions infrastructureVersions;
    private final DuperModelInfraApi duperModel;

    @Inject
    public InfraDeployerImpl(NodeRepository nodeRepository, Provisioner provisioner, DuperModelInfraApi duperModel) {
        this.nodeRepository = nodeRepository;
        this.provisioner = provisioner;
        this.infrastructureVersions = nodeRepository.infrastructureVersions();
        this.duperModel = duperModel;
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
            try {
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

            try (Mutex lock = nodeRepository.applications().lock(application.getApplicationId())) {
                NodeType nodeType = application.getCapacity().type();
                Version targetVersion = infrastructureVersions.getTargetVersionFor(nodeType);
                hostSpecs = provisioner.prepare(application.getApplicationId(),
                                                application.getClusterSpecWithVersion(targetVersion),
                                                application.getCapacity(),
                                                logger::log);

                prepared = true;
            }
        }

        @Override
        public long activate() {
            try (var lock = provisioner.lock(application.getApplicationId())) {
                prepare();

                if (hostSpecs.isEmpty()) {
                    logger.log(Level.FINE, () -> "No nodes to provision for " + application.getCapacity().type() + ", removing application");
                    removeApplication(application.getApplicationId());
                } else {
                    NestedTransaction nestedTransaction = new NestedTransaction();
                    provisioner.activate(hostSpecs, new ActivationContext(0), new ApplicationTransaction(lock, nestedTransaction));
                    nestedTransaction.commit();

                    duperModel.infraApplicationActivated(
                            application.getApplicationId(),
                            hostSpecs.stream().map(HostSpec::hostname).map(HostName::of).collect(Collectors.toList()));

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
