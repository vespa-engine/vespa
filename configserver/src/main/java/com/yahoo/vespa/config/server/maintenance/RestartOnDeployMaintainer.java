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
        Set<String> hostnames = restarts.hostnames();

        if (hostnames.isEmpty()) {
            return restarts;
        }

        Map<String, List<ServiceConfigState>> statesByHostname = serviceConfigStateFetcher.apply(hostnames);

        // For each node, find the lowest generation with pending restart.
        Map<String, Long> pendingRestartGenerationByHostname = restarts.generationsForRestarts().entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(hostname -> Map.entry(hostname, entry.getKey())))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (gen1, gen2) -> gen1 < gen2 ? gen1 : gen2));

        // Two conditions that prevent a node from restarting:
        // 1. Any service on this node with empty applyOnRestart that hasn't reached node generation,
        // which means that it hasn't received and applied a new config yet.
        // 2. Any service on this node with applyOnRestart set to false,
        // which means that it hasn't received a config that requires a restart yet.
        Set<String> nodesToRestart = hostnames.stream()
                .filter(hostname -> {
                    List<ServiceConfigState> states = statesByHostname.get(hostname);

                    if (states == null
                            || states.stream()
                                    .noneMatch(state -> state.applyOnRestart().isPresent())) {
                        log.fine(() -> Text.format(
                                "Node %s of %s has no service states preventing restart.",
                                hostname, id.toFullString()));
                        return true;
                    }

                    var pendingRestartGeneration = pendingRestartGenerationByHostname.get(hostname);

                    if (states.stream()
                            .anyMatch(state -> state.applyOnRestart().isEmpty()
                                    && state.currentGeneration() < pendingRestartGeneration)) {
                        log.fine(() -> Text.format(
                                "Node %s of %s has service states without applyOnRestart that haven't reached node"
                                        + " generation %d, preventing restart.",
                                hostname, id.toFullString(), pendingRestartGeneration));
                        return false;
                    }

                    if (states.stream()
                            .anyMatch(state -> !state.applyOnRestart().orElse(true))) {
                        log.fine(() -> Text.format(
                                "Node %s of %s has service states with applyOnRestart set to false, preventing"
                                        + " restart.",
                                hostname, id.toFullString()));
                        return false;
                    }

                    return true;
                })
                .collect(Collectors.toSet());

        if (nodesToRestart.isEmpty()) {
            log.info(String.format("No nodes of %s are ready for restart yet.", id.toFullString()));
            return restarts;
        }

        restarter.accept(id, nodesToRestart);
        log.info(String.format(
                "Scheduled restart of %d nodes of %s: %s",
                nodesToRestart.size(),
                id.toFullString(),
                nodesToRestart.stream().sorted().collect(Collectors.joining(", "))));

        return restarts.withoutHostnames(nodesToRestart);
    }
}
