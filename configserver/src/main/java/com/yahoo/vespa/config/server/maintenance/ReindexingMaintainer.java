// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.application.ApplicationCuratorDatabase;
import com.yahoo.vespa.config.server.application.ApplicationReindexing;
import com.yahoo.vespa.config.server.application.ConfigConvergenceChecker;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.yolean.Exceptions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Watches pending reindexing, and sets these to ready when config convergence is observed.
 *
 * @author jonmv
 */
public class ReindexingMaintainer extends ConfigServerMaintainer {

    private static final Logger log = Logger.getLogger(ReindexingMaintainer.class.getName());

    /** Timeout per service when getting config generations. */
    private static final Duration timeout = Duration.ofSeconds(10);

    private final ConfigConvergenceChecker convergence;
    private final Clock clock;

    public ReindexingMaintainer(ApplicationRepository applicationRepository, Curator curator, FlagSource flagSource,
                                Duration interval, ConfigConvergenceChecker convergence, Clock clock) {
        super(applicationRepository, curator, flagSource, clock.instant(), interval);
        this.convergence = convergence;
        this.clock = clock;
    }

    @Override
    protected boolean maintain() {
        AtomicBoolean success = new AtomicBoolean(true);
        for (Tenant tenant : applicationRepository.tenantRepository().getAllTenants()) {
            ApplicationCuratorDatabase database = tenant.getApplicationRepo().database();
            for (ApplicationId id : database.activeApplications())
                applicationRepository.getActiveApplicationSet(id)
                                     .map(application -> application.getForVersionOrLatest(Optional.empty(), clock.instant()))
                                     .ifPresent(application -> {
                                         try {
                                             Collection<Long> generations = convergence.getServiceConfigGenerations(application, timeout).values();
                                             try (Lock lock = database.lock(id)) {
                                                 ApplicationReindexing reindexing = database.readReindexingStatus(id);
                                                 database.writeReindexingStatus(id, withReady(reindexing, generations, clock.instant()));
                                             }
                                         }
                                         catch (RuntimeException e) {
                                             log.log(Level.INFO, "Failed to update reindexing status for " + id + ": " + Exceptions.toMessageString(e));
                                             success.set(false);
                                         }
                                     });
        }
        return success.get();
    }

    static ApplicationReindexing withReady(ApplicationReindexing reindexing, Collection<Long> generations, Instant now) {
        long oldestGeneration = generations.stream().min(Comparator.naturalOrder()).orElse(-1L);
        for (var cluster : reindexing.clusters().entrySet())
            for (var pending : cluster.getValue().pending().entrySet())
                if (pending.getValue() <= oldestGeneration)
                    reindexing = reindexing.withReady(cluster.getKey(), pending.getKey(), now);

        return reindexing;
    }

}
