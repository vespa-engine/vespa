package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.ApplicationCuratorDatabase;
import com.yahoo.vespa.config.server.application.ConfigConvergenceChecker.ServiceListResponse;
import com.yahoo.vespa.config.server.application.PendingRestarts;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.yolean.Exceptions;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author jonmv
 */
public class PendingRestartsMaintainer extends ConfigServerMaintainer {

    private final Clock clock;

    public PendingRestartsMaintainer(ApplicationRepository applicationRepository, Curator curator, Clock clock, Duration interval) {
        super(applicationRepository, curator, applicationRepository.flagSource(), clock, interval, true);
        this.clock = clock;
    }

    @Override
    protected double maintain() {
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        for (Tenant tenant : applicationRepository.tenantRepository().getAllTenants()) {
            ApplicationCuratorDatabase database = tenant.getApplicationRepo().database();
            for (ApplicationId id : database.activeApplications())
                applicationRepository.getActiveApplicationVersions(id)
                                     .map(application -> application.getForVersionOrLatest(Optional.empty(), clock.instant()))
                                     .ifPresent(application -> {
                                         try {
                                             attempts.incrementAndGet();
                                             applicationRepository.modifyPendingRestarts(id, restarts ->
                                                     triggerPendingRestarts(restartingHosts -> convergenceOf(application, restartingHosts),
                                                                            this::restart,
                                                                            id,
                                                                            restarts,
                                                                            log));
                                         }
                                         catch (RuntimeException e) {
                                             log.log(Level.INFO, "Failed to update reindexing status for " + id + ": " + Exceptions.toMessageString(e));
                                             failures.incrementAndGet();
                                         }
                                     });
        }
        return asSuccessFactorDeviation(attempts.get(), failures.get());
    }

    private ServiceListResponse convergenceOf(Application application, Set<String> restartingHosts) {
        return applicationRepository.configConvergenceChecker().checkConvergenceUnlessDeferringChangesUntilRestart(application, restartingHosts);
    }

    private void restart(ApplicationId id, Set<String> nodesToRestart) {
        applicationRepository.restart(id, HostFilter.from(nodesToRestart));
    }

    static PendingRestarts triggerPendingRestarts(Function<Set<String>, ServiceListResponse> convergenceChecker,
                                                  BiConsumer<ApplicationId, Set<String>> restarter,
                                                  ApplicationId id,
                                                  PendingRestarts restarts,
                                                  Logger log) {
        Set<String> restartingHosts = restarts.hostnames();
        if (restartingHosts.isEmpty()) return restarts;

        ServiceListResponse convergence = convergenceChecker.apply(restartingHosts);
        long lowestGeneration = convergence.currentGeneration;
        Set<String> nodesToRestart = restarts.restartsReadyAt(lowestGeneration);
        if (nodesToRestart.isEmpty()) {
            log.info(String.format("Cannot yet restart nodes of %s, as some services are still on generation %d:\n\t%s",
                                   id.toFullString(),
                                   lowestGeneration,
                                   convergence.services().stream()
                                              .filter(service -> service.currentGeneration == lowestGeneration)
                                              .map(service -> service.serviceInfo.getHostName() + ":" + service.serviceInfo.getServiceName())
                                              .collect(Collectors.joining("\n\t"))));
            return restarts;
        }

        restarter.accept(id, nodesToRestart);
        log.info(String.format("Scheduled restart of %d nodes after observing generation %d: %s",
                               nodesToRestart.size(), lowestGeneration, nodesToRestart.stream().sorted().collect(Collectors.joining(", "))));

        return restarts.withoutPreviousGenerations(lowestGeneration);
    }

}
