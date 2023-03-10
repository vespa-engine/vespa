// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployer;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        return deployer().lastDeployTime(application)
                // Don't deploy if a regular deploy just happened
                .map(lastDeployTime -> lastDeployTime.isBefore(nodeRepository().clock().instant().minus(minTimeBetweenRedeployments)))
                // We only know last deploy time for applications that were deployed on this config server,
                // the rest will be deployed on another config server
                .orElse(false);
    }

    @Override
    protected Map<ApplicationId, String> applicationsNeedingMaintenance() {
        if (deployer().bootstrapping()) return Map.of();

        // Collect all deployment times before sorting as deployments may happen while we build the set, breaking
        // the comparable contract. Stale times are fine as the time is rechecked in ApplicationMaintainer#deployNow
        Map<ApplicationId, Instant> deploymentTimes = nodesNeedingMaintenance().stream()
                                                                               .map(node -> node.allocation().get().owner())
                                                                               .distinct()
                                                                               .filter(this::canDeployNow)
                                                                               .collect(Collectors.toMap(Function.identity(), this::getLastDeployTime));

        return deploymentTimes.entrySet().stream()
                              .sorted(Map.Entry.comparingByValue())
                              .map(Map.Entry::getKey)
                              .filter(this::shouldMaintain)
                              .collect(Collectors.toMap(applicationId -> applicationId, applicationId -> "current deployment being too old"));
    }

    private boolean shouldMaintain(ApplicationId id) {
        BooleanFlag skipMaintenanceDeployment = PermanentFlags.SKIP_MAINTENANCE_DEPLOYMENT.bindTo(flagSource)
                .with(FetchVector.Dimension.APPLICATION_ID, id.serializedForm());
        return ! skipMaintenanceDeployment.value();
    }

    protected NodeList nodesNeedingMaintenance() {
        return nodeRepository().nodes().list(Node.State.active);
    }

}
