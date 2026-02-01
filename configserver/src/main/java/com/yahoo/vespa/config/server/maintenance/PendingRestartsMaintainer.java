package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.config.model.api.ServiceConfigState;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.ApplicationCuratorDatabase;
import com.yahoo.vespa.config.server.application.ConfigConvergenceChecker.ServiceListResponse;
import com.yahoo.vespa.config.server.application.PendingRestarts;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.yolean.Exceptions;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vespa.flags.Dimension.INSTANCE_ID;

/**
 * @author Jon Marius Venstad
 * @author glebashnik
 */
public class PendingRestartsMaintainer extends ConfigServerMaintainer {

    private final Clock clock;
    private final BooleanFlag waitForApplyOnRestart;

    public PendingRestartsMaintainer(ApplicationRepository applicationRepository, Curator curator, Clock clock, Duration interval) {
        super(applicationRepository, curator, applicationRepository.flagSource(), clock, interval, true);
        this.clock = clock;
        this.waitForApplyOnRestart = Flags.WAIT_FOR_APPLY_ON_RESTART.bindTo(applicationRepository.flagSource());
    }

    @Override
    protected double maintain() {
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        for (Tenant tenant : tenantRepository.getAllTenants()) {
            ApplicationCuratorDatabase database = tenant.getApplicationRepo().database();
            for (ApplicationId id : database.activeApplications())
                applicationRepository
                        .getActiveApplicationVersions(id)
                        .map(application -> application.getForVersionOrLatest(Optional.empty(), clock.instant()))
                        .ifPresent(application -> {
                            try {
                                attempts.incrementAndGet();
                                applicationRepository.modifyPendingRestarts(
                                        id,
                                        restarts -> triggerPendingRestarts(
                                                restartingHosts -> convergenceOf(application, restartingHosts),
                                                restartingHosts -> configStatesOf(application, restartingHosts),
                                                this::restart,
                                                id,
                                                restarts,
                                                waitForApplyOnRestart.with(INSTANCE_ID, id.serializedForm()).value(),
                                                log));
                            } catch (RuntimeException e) {
                                log.log(
                                        Level.INFO,
                                        "Failed to update reindexing status for " + id + ": "
                                                + Exceptions.toMessageString(e));
                                failures.incrementAndGet();
                            }
                        });
        }
        return asSuccessFactorDeviation(attempts.get(), failures.get());
    }

    private ServiceListResponse convergenceOf(Application application, Set<String> restartingHostnames) {
        return applicationRepository.configConvergenceChecker().checkConvergenceUnlessDeferringChangesUntilRestart(application, restartingHostnames);
    }

    private Map<String, ServiceConfigState> configStatesOf(Application application, Set<String> restartingHostnames) {
        Map<ServiceInfo, ServiceConfigState> configStates = applicationRepository
                .configConvergenceChecker()
                .getServiceConfigStates(application, Duration.ofSeconds(10), restartingHostnames);
        return configStates.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().getHostName(), Map.Entry::getValue));
    }

    private void restart(ApplicationId id, Set<String> nodesToRestart) {
        applicationRepository.restart(id, HostFilter.from(nodesToRestart));
    }

    /**
     * Check which pending restarts can be triggered based on (1) observed config generation of other nodes
     * and (2) observed applyOnRestart flag in config state.
     */
    static PendingRestarts triggerPendingRestarts(Function<Set<String>, ServiceListResponse> convergenceChecker,
                                                  Function<Set<String>, Map<String, ServiceConfigState>> configStateChecker,
                                                  BiConsumer<ApplicationId, Set<String>> restarter,
                                                  ApplicationId id,
                                                  PendingRestarts restarts,
                                                  boolean waitForApplyOnRestart,
                                                  Logger log) {
        Set<String> restartingHosts = restarts.hostnames();
        if (restartingHosts.isEmpty()) return restarts;

        // Select nodes whose pending restart generation has been reached by the observed lowest generation among other nodes.
        ServiceListResponse convergence = convergenceChecker.apply(restartingHosts);
        long lowestGeneration = convergence.currentGeneration;
        Set<String> nodesToRestart = restarts.restartsReadyAt(lowestGeneration);

        if (nodesToRestart.isEmpty()) {
            log.info(String.format(
                    "Cannot yet restart nodes of %s, as some services are still on generation %d:\n\t%s",
                    id.toFullString(),
                    lowestGeneration,
                    convergence.services().stream()
                            .filter(service -> service.serviceConfigState.currentGeneration() == lowestGeneration)
                            .map(service ->
                                    service.serviceInfo.getHostName() + ":" + service.serviceInfo.getServiceName())
                            .collect(Collectors.joining("\n\t"))));
            return restarts;
        }

        // From these nodes, select the ones with applyOnRestart set to true (or empty for backwards compatibility).
        if (waitForApplyOnRestart) {
            Map<String, ServiceConfigState> configStates = configStateChecker.apply(nodesToRestart);
            nodesToRestart = configStates.entrySet().stream()
                    .filter(entry -> entry.getValue().applyOnRestart().orElse(true))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            if (nodesToRestart.isEmpty()) {
                log.info(String.format(
                        "No nodes of %s at generation %d have received configs that require restart",
                        id.toFullString(),
                        lowestGeneration));
                return restarts;
            }
        }

        restarter.accept(id, nodesToRestart);
        log.info(String.format(
                "Scheduled restart of %d nodes after observing generation %d: %s",
                nodesToRestart.size(),
                lowestGeneration,
                nodesToRestart.stream().sorted().collect(Collectors.joining(", "))));

        if (waitForApplyOnRestart) {
            return restarts.withoutHostnames(nodesToRestart);
        }
        
        return restarts.withoutPreviousGenerations(lowestGeneration);
    }

}
