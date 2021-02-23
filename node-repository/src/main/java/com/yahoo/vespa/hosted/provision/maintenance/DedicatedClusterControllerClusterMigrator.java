// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Environment;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.orchestrator.Orchestrator;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * @author jonmv
 */
public class DedicatedClusterControllerClusterMigrator extends ApplicationMaintainer {

    private final BooleanFlag flag;
    private final Orchestrator orchestrator;

    protected DedicatedClusterControllerClusterMigrator(Deployer deployer, Metric metric, NodeRepository nodeRepository,
                                                        Duration interval, FlagSource flags, Orchestrator orchestrator) {
        super(deployer, metric, nodeRepository, interval);
        this.flag = Flags.DEDICATED_CLUSTER_CONTROLLER_CLUSTER.bindTo(flags);
        this.orchestrator = orchestrator;
    }

    @Override
    protected Set<ApplicationId> applicationsNeedingMaintenance() {
        if (deployer().bootstrapping()) return Set.of();

        ZonedDateTime date = ZonedDateTime.ofInstant(clock().instant(), java.time.ZoneId.of("Europe/Oslo"));
        if (   ! nodeRepository().zone().system().isCd()
            &&   nodeRepository().zone().environment() != Environment.staging
            &&   (List.of(SATURDAY, SUNDAY).contains(date.getDayOfWeek()) || date.getHour() < 8 || 12 < date.getHour()))
            return Set.of();

        return nodeRepository().applications().ids().stream()
                               .sorted()
                               .filter(this::isEligible)
                               .filter(this::hasNotSwitched)
                               .filter(this::isQuiescent)
                               .limit(1)
                               .collect(toUnmodifiableSet());
    }

    @Override
    protected void deploy(ApplicationId id) {
        migrate(id);
        super.deploy(id);
    }

    private boolean isEligible(ApplicationId id) {
        return    deployer().lastDeployTime(id).map(at -> at.isBefore(clock().instant().minus(Duration.ofMinutes(10)))).orElse(false)
               && flag.with(FetchVector.Dimension.APPLICATION_ID, id.serializedForm()).value();
    }

    private boolean hasNotSwitched(ApplicationId id) {
        return ! deployer().getDedicatedClusterControllerCluster(id);
    }

    private boolean isQuiescent(ApplicationId id) {
        return orchestrator.isQuiescent(id); // Check all content nodes are UP, have wanted state UP, and can be moved to MAINTENANCE.
    }

    private void migrate(ApplicationId id) {
        log.log(Level.INFO, "Migrating " + id + " to dedicated cluster controller cluster");
        deployer().setDedicatedClusterControllerCluster(id);
    }

}
