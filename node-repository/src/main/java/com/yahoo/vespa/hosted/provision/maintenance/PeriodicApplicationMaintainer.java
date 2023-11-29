// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployer;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.Dimension;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.Map;

import static java.util.logging.Level.INFO;
import static java.util.stream.Collectors.toMap;

/**
 * The application maintainer regularly redeploys all applications to make sure the node repo and application
 * model is in sync and to trigger background node allocation changes such as allocation optimizations and
 * flavor retirement.
 *
 * @author bratseth
 */
public class PeriodicApplicationMaintainer extends ApplicationMaintainer {

    private final Duration minTimeBetweenRedeployments;
    private final FlagSource flagSource;

    PeriodicApplicationMaintainer(Deployer deployer, Metric metric, NodeRepository nodeRepository,
                                  Duration interval, Duration minTimeBetweenRedeployments, FlagSource flagSource) {
        super(deployer, metric, nodeRepository, interval);
        this.minTimeBetweenRedeployments = minTimeBetweenRedeployments;
        this.flagSource = flagSource;
    }

    @Override
    protected boolean canDeployNow(ApplicationId application) {
        try {
            return deployer().deployTime(application)
                    .map(lastDeployTime ->    lastDeployTime.isBefore(nodeRepository().clock().instant().minus(minTimeBetweenRedeployments))
                            || deployer().readiedReindexingAfter(application, lastDeployTime))
                    .orElse(false);
        }
        catch (Exception e) {
            log.log(INFO, () -> "Failed finding last deploy time or reindexing status for " + application + Exceptions.toMessageString(e));
            throw e;
        }
    }

    @Override
    protected Map<ApplicationId, String> applicationsNeedingMaintenance() {
        if (deployer().bootstrapping()) return Map.of();

        return nodesNeedingMaintenance().stream()
                                        .map(node -> node.allocation().get().owner())
                                        .distinct()
                                        .filter(this::shouldMaintain)
                                        .filter(this::canDeployNow)
                                        .collect(toMap(applicationId -> applicationId, applicationId -> "current deployment being too old"));
    }

    private boolean shouldMaintain(ApplicationId id) {
        BooleanFlag skipMaintenanceDeployment = PermanentFlags.SKIP_MAINTENANCE_DEPLOYMENT.bindTo(flagSource)
                .with(Dimension.INSTANCE_ID, id.serializedForm());
        return ! skipMaintenanceDeployment.value();
    }

    protected NodeList nodesNeedingMaintenance() {
        return nodeRepository().nodes().list(Node.State.active);
    }

}
