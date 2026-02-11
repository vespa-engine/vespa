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

import static com.yahoo.vespa.flags.Dimension.INSTANCE_ID;

/**
 * Maintainer that triggers restart of nodes with pending restarts.
 * The maintainer also updates pending restart nodes in ZooKeeper.
 * The node restart is usually needed for services with config changes that are applied on restart.
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
                boolean shouldWaitForApplyOnRestart = waitForApplyOnRestart
                        .with(INSTANCE_ID, id.serializedForm())
                        .value();

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
                                                    "Failed to update pending restarts for %d: %s",
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

        Map<String, List<ServiceConfigState>> statesByHostname = serviceConfigStateFetcher.apply(restarts.hostnames());

        // Minimum observed generation for all services without applyOnRestart across all pending restart nodes.
        // Services with applyOnRestart are excluded because they can be waiting for restart to apply a new
        // config.
        OptionalLong minStateGeneration = statesByHostname.values().stream()
                .flatMap(List::stream)
                .filter(state -> state.applyOnRestart().isEmpty())
                .mapToLong(ServiceConfigState::currentGeneration)
                .min();

        // This will be used as a fallback if minStateGeneration is empty.
        OptionalLong maxRestartGeneration = restarts.generationsForRestarts().keySet().stream()
                .mapToLong(Long::longValue)
                .max();

        // Should be present, otherwise there is nothing to restart.
        if (maxRestartGeneration.isEmpty()) {
            return restarts;
        }

        // Without any services without applyOnRestart,
        // nothing holding us back from restarting with the maximum restart generation.
        // This assumes that services will get the latest config after restart.
        // There is no guarantee for that, but it is the best we can do.
        long readyGeneration = minStateGeneration.orElse(maxRestartGeneration.getAsLong());

        // Select nodes with restart generations that are less or equal to the ready generation.
        // Nodes that only have greater restart generations need to wait until we reach that generation.
        Set<String> nodesToRestart = restarts.restartsReadyAt(readyGeneration);

        // For each node, check if all it's services with (non-empty) applyOnRestart
        // either reached the ready generation or have restartOnDeploy set true.
        // If not, it means that the service hasn't received a new config that requires a restart yet.
        nodesToRestart = nodesToRestart.stream()
                .filter(hostname -> {
                    List<ServiceConfigState> states = statesByHostname.get(hostname);
                    return states == null
                            || states.stream()
                                    .filter(state -> state.applyOnRestart().isPresent())
                                    .allMatch(state -> state.currentGeneration() >= readyGeneration
                                            || state.applyOnRestart().get());
                })
                .collect(Collectors.toSet());

        if (nodesToRestart.isEmpty()) {
            log.info(Text.format(
                    "No nodes of %s are ready for restart at generation %d.", id.toFullString(), readyGeneration));
            return restarts;
        }

        restarter.accept(id, nodesToRestart);
        log.info(Text.format(
                "Scheduled restart of %d nodes of %s at generation %d: %s",
                nodesToRestart.size(),
                id.toFullString(),
                readyGeneration,
                nodesToRestart.stream().sorted().collect(Collectors.joining(", "))));

        return restarts.withoutPreviousGeneration(readyGeneration, nodesToRestart);
    }
}
