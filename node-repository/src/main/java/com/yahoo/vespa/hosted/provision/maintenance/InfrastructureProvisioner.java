// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.log.LogLevel;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.service.monitor.application.ConfigServerApplication;
import com.yahoo.vespa.service.monitor.application.ConfigServerHostApplication;
import com.yahoo.vespa.service.monitor.application.HostedVespaApplication;
import com.yahoo.vespa.service.monitor.application.ProxyHostApplication;

import java.time.Duration;
import java.util.Arrays;
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
    private static final List<HostedVespaApplication> HOSTED_VESPA_APPLICATIONS = Arrays.asList(
            ConfigServerApplication.CONFIG_SERVER_APPLICATION,
            ConfigServerHostApplication.CONFIG_SERVER_HOST_APPLICATION,
            ProxyHostApplication.PROXY_HOST_APPLICATION);

    private final Provisioner provisioner;
    private final InfrastructureVersions infrastructureVersions;

    public InfrastructureProvisioner(Provisioner provisioner, NodeRepository nodeRepository,
                                     InfrastructureVersions infrastructureVersions, Duration interval, JobControl jobControl) {
        super(nodeRepository, interval, jobControl);
        this.provisioner = provisioner;
        this.infrastructureVersions = infrastructureVersions;
    }

    @Override
    protected void maintain() {
        for (HostedVespaApplication application: HOSTED_VESPA_APPLICATIONS) {
            try (Mutex lock = nodeRepository().lock(application.getApplicationId())) {
                Optional<Version> version = getTargetVersion(application.getCapacity().type());
                if (! version.isPresent()) continue;

                List<HostSpec> hostSpecs = provisioner.prepare(
                        application.getApplicationId(),
                        application.getClusterSpecWithVersion(version.get()),
                        application.getCapacity(),
                        1, // groups
                        logger::log);

                NestedTransaction nestedTransaction = new NestedTransaction();
                provisioner.activate(nestedTransaction, application.getApplicationId(), hostSpecs);
                nestedTransaction.commit();
            }
        }
    }

    /**
     * Returns the version that the given node type should be provisioned to. This is
     * the version returned by {@link InfrastructureVersions#getTargetVersionFor} unless a provisioning is:
     * <ul>
     *   <li>not possible: no nodes of given type in legal state in node-repo</li>
     *   <li>redundant: all nodes that can be provisioned already have the right wanted Vespa version</li>
     * </ul>
     */
    Optional<Version> getTargetVersion(NodeType nodeType) {
        Optional<Version> targetVersion = infrastructureVersions.getTargetVersionFor(nodeType);
        if (!targetVersion.isPresent()) {
            logger.log(LogLevel.DEBUG, "Skipping provision of " + nodeType + ": No target version set");
            return Optional.empty();
        }

        List<Version> wantedVersions = nodeRepository().getNodes(nodeType,
                Node.State.ready, Node.State.reserved, Node.State.active, Node.State.inactive).stream()
                .map(node -> node.allocation()
                        .map(allocation -> allocation.membership().cluster().vespaVersion())
                        .orElse(null))
                .collect(Collectors.toList());
        if (wantedVersions.isEmpty()) {
            logger.log(LogLevel.DEBUG, "Skipping provision of " + nodeType + ": No nodes to provision");
            return Optional.empty();
        }

        if (wantedVersions.stream().allMatch(targetVersion.get()::equals)) {
            logger.log(LogLevel.DEBUG, "Skipping provision of " + nodeType +
                    ": Already provisioned to target version " + targetVersion);
            return Optional.empty();
        }
        return targetVersion;
    }
}
