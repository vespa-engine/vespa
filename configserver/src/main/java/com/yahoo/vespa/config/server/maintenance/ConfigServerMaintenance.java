// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.concurrent.maintenance.Maintainer;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.ConfigServerBootstrap;
import com.yahoo.vespa.config.server.application.ConfigConvergenceChecker;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.FlagSource;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Maintenance jobs of the config server.
 * Each maintenance job is a singleton instance of its implementing class, created and owned by this,
 * and running its own dedicated thread. {@link ConfigServerBootstrap} is injected into this class, so
 * no maintainers will run until bootstrapping is done
 *
 * @author hmusum
 */
public class ConfigServerMaintenance {

    private final List<Maintainer> maintainers = new CopyOnWriteArrayList<>();
    private final ConfigserverConfig configserverConfig;
    private final ApplicationRepository applicationRepository;
    private final Curator curator;
    private final FlagSource flagSource;
    private final ConfigConvergenceChecker convergenceChecker;

    public ConfigServerMaintenance(ApplicationRepository applicationRepository) {
        this.configserverConfig = applicationRepository.configserverConfig();
        this.applicationRepository = applicationRepository;
        this.curator = applicationRepository.tenantRepository().getCurator();
        this.flagSource = applicationRepository.flagSource();
        this.convergenceChecker = applicationRepository.configConvergenceChecker();
    }

    public void startBeforeBootstrap() {
        maintainers.add(new ApplicationPackageMaintainer(applicationRepository, curator, Duration.ofSeconds(30), flagSource));
        maintainers.add(new TenantsMaintainer(applicationRepository, curator, flagSource,
                                              new DefaultTimes(configserverConfig).defaultInterval, Clock.systemUTC()));
    }

    public void startAfterBootstrap() {
        maintainers.add(new FileDistributionMaintainer(applicationRepository, curator,
                                                       new DefaultTimes(configserverConfig).defaultInterval, flagSource));
        maintainers.add(new SessionsMaintainer(applicationRepository, curator, Duration.ofSeconds(30), flagSource));
        maintainers.add(new ReindexingMaintainer(applicationRepository, curator, flagSource,
                                                 Duration.ofMinutes(3), convergenceChecker, Clock.systemUTC()));
    }

    public void shutdown() {
        maintainers.forEach(Maintainer::shutdown);
        maintainers.forEach(Maintainer::awaitShutdown);
    }

    public List<Maintainer> maintainers() { return List.copyOf(maintainers); }

    /*
     * Default values from config. If one of the values needs to be changed, add the value to
     * configserver-config.xml in the config server application directory and restart the config server
     */
    private static class DefaultTimes {

        private final Duration defaultInterval;

        DefaultTimes(ConfigserverConfig configserverConfig) {
            this.defaultInterval = Duration.ofMinutes(configserverConfig.maintainerIntervalMinutes());
        }
    }

}
