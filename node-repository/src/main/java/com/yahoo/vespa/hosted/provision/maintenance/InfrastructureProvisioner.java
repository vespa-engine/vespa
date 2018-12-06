// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.log.LogLevel;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.service.monitor.DuperModelInfraApi;
import com.yahoo.vespa.service.monitor.InfraApplicationApi;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This maintainer makes sure that infrastructure nodes are allocated with correct wanted
 * version. Source for the wanted version comes from the target version set using
 * /nodes/v2/upgrade/ endpoint.
 *
 * @author freva
 */
public class InfrastructureProvisioner extends Maintainer {

    private static final Logger logger = Logger.getLogger(InfrastructureProvisioner.class.getName());

    private final Provisioner provisioner;
    private final InfrastructureVersions infrastructureVersions;
    private final DuperModelInfraApi duperModel;

    public InfrastructureProvisioner(Provisioner provisioner, NodeRepository nodeRepository,
                                     InfrastructureVersions infrastructureVersions, Duration interval, JobControl jobControl,
                                     DuperModelInfraApi duperModel) {
        super(nodeRepository, interval, jobControl);
        this.provisioner = provisioner;
        this.infrastructureVersions = infrastructureVersions;
        this.duperModel = duperModel;
    }

    @Override
    protected void maintain() {
        for (InfraApplicationApi application: duperModel.getSupportedInfraApplications()) {
            try (Mutex lock = nodeRepository().lock(application.getApplicationId())) {
                NodeType nodeType = application.getCapacity().type();

                Optional<Version> targetVersion = infrastructureVersions.getTargetVersionFor(nodeType);
                if (!targetVersion.isPresent()) {
                    logger.log(LogLevel.DEBUG, "Skipping provision of " + nodeType + ": No target version set");
                    duperModel.infraApplicationRemoved(application.getApplicationId());
                    continue;
                }

                List<Version> wantedVersions = nodeRepository()
                        .getNodes(nodeType, Node.State.ready, Node.State.reserved, Node.State.active, Node.State.inactive)
                        .stream()
                        .map(node -> node.allocation()
                                .map(allocation -> allocation.membership().cluster().vespaVersion())
                                .orElse(null))
                        .collect(Collectors.toList());
                if (wantedVersions.isEmpty()) {
                    // TODO: Unprovision active nodes from application?
                    logger.log(LogLevel.DEBUG, "Skipping provision of " + nodeType + ": No nodes to provision");
                    duperModel.infraApplicationRemoved(application.getApplicationId());
                    continue;
                }

                if (wantedVersions.stream().allMatch(targetVersion.get()::equals) &&
                        duperModel.infraApplicationIsActive(application.getApplicationId())) {
                    logger.log(LogLevel.DEBUG, "Skipping provision of " + nodeType +
                            ": Already provisioned to target version " + targetVersion);
                    continue;
                }

                List<HostSpec> hostSpecs = provisioner.prepare(
                        application.getApplicationId(),
                        application.getClusterSpecWithVersion(targetVersion.get()),
                        application.getCapacity(),
                        1, // groups
                        logger::log);

                NestedTransaction nestedTransaction = new NestedTransaction();
                provisioner.activate(nestedTransaction, application.getApplicationId(), hostSpecs);
                nestedTransaction.commit();

                duperModel.infraApplicationActivated(
                        application.getApplicationId(),
                        hostSpecs.stream().map(HostSpec::hostname).map(HostName::from).collect(Collectors.toList()));
            } catch (RuntimeException e) {
                logger.log(LogLevel.INFO, "Failed to activate " + application.getApplicationId(), e);
                // loop around to activate the next application
            }
        }
    }
}
