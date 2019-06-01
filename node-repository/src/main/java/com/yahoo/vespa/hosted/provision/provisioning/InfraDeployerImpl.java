// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.google.inject.Inject;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InfraDeployer;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.log.LogLevel;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.maintenance.InfrastructureVersions;
import com.yahoo.vespa.service.monitor.DuperModelInfraApi;
import com.yahoo.vespa.service.monitor.InfraApplicationApi;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    public Map<ApplicationId, Deployment> getSupportedInfraDeployments() {
        return duperModel.getSupportedInfraApplications().stream()
                .collect(Collectors.toMap(InfraApplicationApi::getApplicationId, InfraDeployment::new));
    }

    private class InfraDeployment implements Deployment {
        private final InfraApplicationApi application;

        private boolean prepared = false;
        private List<Node> candidateNodes;
        private List<HostSpec> hostSpecs;

        private InfraDeployment(InfraApplicationApi application) {
            this.application = application;
        }

        @Override
        public void prepare() {
            if (prepared) return;

            try (Mutex lock = nodeRepository.lock(application.getApplicationId())) {
                NodeType nodeType = application.getCapacity().type();

                candidateNodes = nodeRepository
                        .getNodes(nodeType, Node.State.ready, Node.State.reserved, Node.State.active, Node.State.inactive);
                if (candidateNodes.isEmpty()) {
                    logger.log(LogLevel.DEBUG, "No nodes to provision for " + nodeType + ", removing application");
                    removeApplication(application.getApplicationId());
                    return;
                }

                Version targetVersion = infrastructureVersions.getTargetVersionFor(nodeType);
                if (!allActiveNodesOn(targetVersion, candidateNodes)) {
                    hostSpecs = provisioner.prepare(
                            application.getApplicationId(),
                            application.getClusterSpecWithVersion(targetVersion),
                            application.getCapacity(),
                            1, // groups
                            logger::log);
                }

                prepared = true;
            }
        }

        @Override
        public void activate() {
            try (Mutex lock = nodeRepository.lock(application.getApplicationId())) {
                prepare();
                if (candidateNodes == null) return;

                if (hostSpecs != null) {
                    NestedTransaction nestedTransaction = new NestedTransaction();
                    provisioner.activate(nestedTransaction, application.getApplicationId(), hostSpecs);
                    nestedTransaction.commit();
                }

                duperModel.infraApplicationActivated(
                        application.getApplicationId(),
                        candidateNodes.stream().map(Node::hostname).map(HostName::from).collect(Collectors.toList()));

                logger.log(LogLevel.DEBUG, () -> generateActivationLogMessage(candidateNodes, application.getApplicationId()));
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
            NestedTransaction nestedTransaction = new NestedTransaction();
            provisioner.remove(nestedTransaction, applicationId);
            nestedTransaction.commit();
            duperModel.infraApplicationRemoved(applicationId);
        }
    }

    private static boolean allActiveNodesOn(Version version, List<Node> nodes) {
        return nodes.stream()
                .allMatch(node ->
                        node.state() == Node.State.active &&
                        node.allocation()
                                .map(allocation -> allocation.membership().cluster().vespaVersion().equals(version))
                                .orElse(false));
    }

    private static String generateActivationLogMessage(List<Node> nodes, ApplicationId applicationId) {
        String detail;
        if (nodes.size() < 10) {
            detail = ": " + nodes.stream().map(Node::hostname).collect(Collectors.joining(","));
        } else {
            detail = " with " + nodes.size() + " hosts";
        }
        return "Infrastructure application " + applicationId + " activated" + detail;
    }
}
