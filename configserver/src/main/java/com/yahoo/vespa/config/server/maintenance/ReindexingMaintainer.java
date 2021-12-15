// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.ApplicationCuratorDatabase;
import com.yahoo.vespa.config.server.application.ApplicationReindexing;
import com.yahoo.vespa.config.server.application.ApplicationReindexing.Cluster;
import com.yahoo.vespa.config.server.application.ConfigConvergenceChecker;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.yolean.Exceptions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Watches pending reindexing, and sets these to ready when config convergence is observed.
 * Also removes data for clusters or document types which no longer exist.
 *
 * @author jonmv
 */
public class ReindexingMaintainer extends ConfigServerMaintainer {

    private static final Logger log = Logger.getLogger(ReindexingMaintainer.class.getName());

    /** Timeout per service when getting config generations. */
    private static final Duration timeout = Duration.ofSeconds(10);

    /** Relative reindexing speed. */
    static final double SPEED = 1;

    private final ConfigConvergenceChecker convergence;
    private final Clock clock;

    public ReindexingMaintainer(ApplicationRepository applicationRepository, Curator curator, FlagSource flagSource,
                                Duration interval, ConfigConvergenceChecker convergence, Clock clock) {
        super(applicationRepository, curator, flagSource, clock.instant(), interval, true);
        this.convergence = convergence;
        this.clock = clock;
    }

    @Override
    protected double maintain() {
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        for (Tenant tenant : applicationRepository.tenantRepository().getAllTenants()) {
            ApplicationCuratorDatabase database = tenant.getApplicationRepo().database();
            for (ApplicationId id : database.activeApplications())
                applicationRepository.getActiveApplicationSet(id)
                                     .map(application -> application.getForVersionOrLatest(Optional.empty(), clock.instant()))
                                     .ifPresent(application -> {
                                         try {
                                             attempts.incrementAndGet();
                                             applicationRepository.modifyReindexing(id, reindexing -> {
                                                 reindexing = withNewReady(reindexing, lazyGeneration(application), clock.instant());
                                                 reindexing = withOnlyCurrentData(reindexing, application);
                                                 return reindexing;
                                             });
                                         }
                                         catch (RuntimeException e) {
                                             log.log(Level.INFO, "Failed to update reindexing status for " + id + ": " + Exceptions.toMessageString(e));
                                             failures.incrementAndGet();
                                         }
                                     });
        }
        return asSuccessFactor(attempts.get(), failures.get());
    }

    private Supplier<Long> lazyGeneration(Application application) {
        AtomicLong oldest = new AtomicLong();
        return () -> {
            if (oldest.get() == 0)
                oldest.set(convergence.getServiceConfigGenerations(application, timeout).values().stream()
                                      .min(Comparator.naturalOrder())
                                      .orElse(-1L));

            return oldest.get();
        };
    }

    static ApplicationReindexing withNewReady(ApplicationReindexing reindexing, Supplier<Long> oldestGeneration, Instant now) {
        // Config convergence means reindexing of detected reindex actions may begin.
        for (var cluster : reindexing.clusters().entrySet())
            for (var pending : cluster.getValue().pending().entrySet())
                if (pending.getValue() <= oldestGeneration.get()) {
                    reindexing = reindexing.withReady(cluster.getKey(), pending.getKey(), now, SPEED)
                                           .withoutPending(cluster.getKey(), pending.getKey());
                }

        return reindexing;
    }

    static ApplicationReindexing withOnlyCurrentData(ApplicationReindexing reindexing, Application application) {
        return withOnlyCurrentData(reindexing, application.getModel().documentTypesByCluster());
    }

    static ApplicationReindexing withOnlyCurrentData(ApplicationReindexing reindexing, Map<String, ? extends Collection<String>> clusterDocumentTypes) {
        for (String clusterId : reindexing.clusters().keySet()) {
            if ( ! clusterDocumentTypes.containsKey(clusterId))
                reindexing = reindexing.without(clusterId);
            else {
                Cluster cluster = reindexing.clusters().get(clusterId);
                Collection<String> documentTypes = clusterDocumentTypes.get(clusterId);
                for (String pending : cluster.pending().keySet())
                    if ( ! documentTypes.contains(pending))
                        reindexing = reindexing.withoutPending(clusterId, pending);
                for (String ready : cluster.ready().keySet())
                    if ( ! documentTypes.contains(ready))
                        reindexing = reindexing.without(clusterId, ready);
            }
        }
        return reindexing;
    }

}
