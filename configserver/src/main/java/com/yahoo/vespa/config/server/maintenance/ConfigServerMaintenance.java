// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.concurrent.maintenance.Maintainer;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.application.ConfigConvergenceChecker;
import com.yahoo.vespa.config.server.filedistribution.FileDirectory;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.FlagSource;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.yahoo.vespa.config.server.filedistribution.FileDistributionUtil.getOtherConfigServersInCluster;

/**
 * Maintenance jobs of the config server.
 * Each maintenance job is a singleton instance of its implementing class, created and owned by this,
 * and running its own dedicated thread.
 *
 * @author hmusum
 */
public class ConfigServerMaintenance {

    private final List<Maintainer> maintainers = new CopyOnWriteArrayList<>();
    private final ApplicationRepository applicationRepository;
    private final Curator curator;
    private final FlagSource flagSource;
    private final ConfigConvergenceChecker convergenceChecker;
    private final FileDirectory fileDirectory;
    private final Duration interval;

    public ConfigServerMaintenance(ApplicationRepository applicationRepository, FileDirectory fileDirectory) {
        this.applicationRepository = applicationRepository;
        this.curator = applicationRepository.tenantRepository().getCurator();
        this.flagSource = applicationRepository.flagSource();
        this.convergenceChecker = applicationRepository.configConvergenceChecker();
        this.fileDirectory = fileDirectory;
        this.interval = Duration.ofMinutes(applicationRepository.configserverConfig().maintainerIntervalMinutes());
    }

    public void startBeforeBootstrap() {
        List<String> otherConfigServersInCluster = getOtherConfigServersInCluster(applicationRepository.configserverConfig());
        if ( ! otherConfigServersInCluster.isEmpty())
            maintainers.add(new ApplicationPackageMaintainer(applicationRepository, curator, Duration.ofSeconds(30),
                                                             flagSource, otherConfigServersInCluster));
        maintainers.add(new TenantsMaintainer(applicationRepository, curator, flagSource, interval, Clock.systemUTC()));
    }

    public void startAfterBootstrap() {
        maintainers.add(new FileDistributionMaintainer(applicationRepository,
                                                       curator,
                                                       interval,
                                                       flagSource,
                                                       fileDirectory));
        maintainers.add(new SessionsMaintainer(applicationRepository, curator, Duration.ofSeconds(30), flagSource));
        maintainers.add(new ReindexingMaintainer(applicationRepository, curator, flagSource,
                                                 Duration.ofMinutes(3), convergenceChecker, Clock.systemUTC()));
    }

    public void shutdown() {
        maintainers.forEach(Maintainer::shutdown);
        maintainers.forEach(Maintainer::awaitShutdown);
    }

    public List<Maintainer> maintainers() { return List.copyOf(maintainers); }

}
