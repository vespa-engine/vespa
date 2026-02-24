// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.api.annotations.Beta;
import com.yahoo.config.model.api.ServiceConfigState;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.text.Text;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.application.ApplicationCuratorDatabase;
import com.yahoo.vespa.config.server.application.PendingRestarts;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.yolean.Exceptions;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Maintainer that triggers restart of pending restart nodes stored in ZooKeeper.
 * Implements restart conditions to ensure that services converge to the same config generation before restart.
 * Removes restarted nodes from ZooKeeper.
 * This is an experimental replacement for {@link PendingRestartsMaintainer}, enabled by
 * {@link Flags#WAIT_FOR_APPLY_ON_RESTART} feature flag.
 *
 * @author glebashnik
 */
@Beta
public class RestartOnDeployMaintainer extends ConfigServerMaintainer {

    private final Clock clock;
    private final BooleanFlag waitForApplyOnRestart;

    public RestartOnDeployMaintainer(
            ApplicationRepository applicationRepository, Curator curator, Clock clock, Duration interval) {
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
            for (ApplicationId id : database.activeApplications()) {
                // Controls whether to use this maintainer for a specific instance with the feature flag.
                // Alternatively, the older PendingRestartsMaintainer will be used.
                boolean shouldWaitForApplyOnRestart =
                        waitForApplyOnRestart.with(id).value();

                if (shouldWaitForApplyOnRestart) {
                    applicationRepository
                            .getActiveApplicationVersions(id)
                            .map(application -> application.getForVersionOrLatest(Optional.empty(), clock.instant()))
                            .ifPresent(application -> {
                                try {
                                    attempts.incrementAndGet();
                                    applicationRepository.modifyPendingRestarts(
                                            id,
                                            restarts -> triggerPendingRestarts(
                                                    restartingHosts -> getConfigServiceStatesByHostname(
                                                            application, restartingHosts),
                                                    this::restart,
                                                    id,
                                                    restarts,
                                                    log));
                                } catch (RuntimeException e) {
                                    log.log(
                                            Level.INFO,
                                            Text.format(
                                                    "Failed to update pending restarts for %s: %s",
                                                    id.toFullString(), Exceptions.toMessageString(e)));
                                    failures.incrementAndGet();
                                }
                            });
                }
            }
        }
        return asSuccessFactorDeviation(attempts.get(), failures.get());
    }

    private Map<String, List<ServiceConfigState>> getConfigServiceStatesByHostname(
            Application application, Set<String> hostnames) {
        Map<ServiceInfo, ServiceConfigState> stateByService = applicationRepository
                .configStateChecker()
                .getServiceConfigStates(application, Duration.ofSeconds(10), hostnames);

        return stateByService.entrySet().stream()
                .collect(Collectors.groupingBy(
                        entry -> entry.getKey().getHostName(),
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private void restart(ApplicationId id, Set<String> hostnames) {
        applicationRepository.restart(id, HostFilter.from(hostnames));
    }

    static PendingRestarts triggerPendingRestarts(
            Function<Set<String>, Map<String, List<ServiceConfigState>>> serviceConfigStateFetcher,
            BiConsumer<ApplicationId, Set<String>> restarter,
            ApplicationId id,
            PendingRestarts restarts,
            Logger log) {
        if (restarts.isEmpty()) {
            return restarts;
        }

        log.fine(() -> Text.format(
                "Pending restarts of %s: %s",
                id.toFullString(),
                restarts.generationsForRestarts().entrySet().stream()
                        .map(entry -> Text.format("%d -> [%s]", entry.getKey(), String.join(", ", entry.getValue())))
                        .collect(Collectors.joining(", "))));

        Map<String, List<ServiceConfigState>> statesByHostname = serviceConfigStateFetcher.apply(restarts.hostnames());

        if (statesByHostname.isEmpty()) {
            log.fine(() -> Text.format("No services states of %s are fetched.", id.toFullString()));
        }

        // Minimum observed config generation for all services without applyOnRestart across all pending restart nodes.
        // Services with applyOnRestart set to {@code true} are excluded because they are waiting for restart to apply a
        // new config and report a new config generation.
        OptionalLong minObservedGeneration = statesByHostname.values().stream()
                .flatMap(List::stream)
                .filter(state -> !state.applyOnRestart().orElse(false))
                .mapToLong(ServiceConfigState::currentGeneration)
                .min();

        long readyGeneration;

        if (minObservedGeneration.isPresent()) {
            readyGeneration = minObservedGeneration.getAsLong();
            log.fine(() -> Text.format(
                    "Ready generation of %s is set to min observed generation %d", id.toFullString(), readyGeneration));
        } else {
            // If all services have applyOnRestart set to {@code true},
            // nothing is holding us back from restarting with the maximum restart generation.
            // This assumes that services will get the latest config after restart.
            // There is no guarantee for that, but it is the best we can do.
            OptionalLong maxRestartGeneration = restarts.generationsForRestarts().keySet().stream()
                    .mapToLong(Long::longValue)
                    .max();

            // Should be present, otherwise there is nothing to restart.
            if (maxRestartGeneration.isEmpty()) {
                return restarts;
            }

            readyGeneration = maxRestartGeneration.getAsLong();
            log.fine(() -> Text.format(
                    "Ready generation of %s is set to max pending restart generation %d",
                    id.toFullString(), readyGeneration));
        }

        // Select nodes with restart generations that are less or equal to the ready generation.
        // Nodes that only have greater restart generations need to wait
        // until services without applyOnRestart (if any) reach the ready generation.
        Set<String> nodesToRestart = restarts.restartsReadyAt(readyGeneration);

        if (nodesToRestart.isEmpty()) {
            log.info(() -> Text.format(
                    "No nodes of %s are ready for restart at generation %d.", id.toFullString(), readyGeneration));
            return restarts;
        }

        restarter.accept(id, nodesToRestart);
        log.info(() -> Text.format(
                "Scheduled restart of %d nodes of %s at generation %d: %s",
                nodesToRestart.size(),
                id.toFullString(),
                readyGeneration,
                nodesToRestart.stream().sorted().collect(Collectors.joining(", "))));

        return restarts.withoutPreviousGenerations(readyGeneration);
    }
}
