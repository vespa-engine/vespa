// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployer;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.provision.NodeRepository;

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

    protected DedicatedClusterControllerClusterMigrator(Deployer deployer, Metric metric, NodeRepository nodeRepository,
                                                        Duration interval, FlagSource flags) {
        super(deployer, metric, nodeRepository, interval);
        this.flag = Flags.DEDICATED_CLUSTER_CONTROLLER_CLUSTER.bindTo(flags);
    }

    @Override
    protected Set<ApplicationId> applicationsNeedingMaintenance() {
        ZonedDateTime date = ZonedDateTime.ofInstant(clock().instant(), java.time.ZoneId.of("Europe/Oslo"));
        if (List.of(SATURDAY, SUNDAY).contains(date.getDayOfWeek()) || date.getHour() < 8 || 12 < date.getHour())
            return Set.of();

        return nodeRepository().applications().ids().stream()
                               .filter(this::isEligible)
                               .filter(this::hasNotSwitched)
                               .filter(this::isQuiescent)
                               .limit(1)
                               .peek(this::migrate)
                               .collect(toUnmodifiableSet());
    }

    private boolean isEligible(ApplicationId id) {
        return flag.with(FetchVector.Dimension.APPLICATION_ID, id.serializedForm()).value();
    }

    private boolean hasNotSwitched(ApplicationId id) {
        return ! deployer().getDedicatedClusterControllerCluster(id);
    }

    private boolean isQuiescent(ApplicationId id) {
        return false; // Check all content nodes are UP, have wanted state UP, and can be moved to MAINTENANCE.
    }

    private void migrate(ApplicationId id) {
        log.log(Level.INFO, "Migrating " + id + " to dedicated cluster controller cluster");
        deployer().setDedicatedClusterControllerCluster(id);
    }

}
