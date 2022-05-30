// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.systemflags;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.provision.SystemName;
import com.yahoo.text.Text;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.hosted.controller.api.integration.ControllerIdentityProvider;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.FlagsTarget;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.SystemFlagsDataArchive;
import com.yahoo.vespa.hosted.controller.restapi.systemflags.SystemFlagsDeployResult.OperationError;
import com.yahoo.vespa.hosted.controller.restapi.systemflags.SystemFlagsDeployResult.Warning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.restapi.systemflags.SystemFlagsDeployResult.FlagDataChange;

/**
 * Deploy a flags data archive to all targets in a given system
 *
 * @author bjorncs
 */
class SystemFlagsDeployer  {

    private static final Logger log = Logger.getLogger(SystemFlagsDeployer.class.getName());

    private final FlagsClient client;
    private final SystemName system;
    private final Set<FlagsTarget> targets;
    private final ExecutorService executor = Executors.newCachedThreadPool(new DaemonThreadFactory("system-flags-deployer-"));


    SystemFlagsDeployer(ControllerIdentityProvider identityProvider, SystemName system, Set<FlagsTarget> targets) {
        this(new FlagsClient(identityProvider, targets), system, targets);
    }

    SystemFlagsDeployer(FlagsClient client, SystemName system, Set<FlagsTarget> targets) {
        this.client = client;
        this.system = system;
        this.targets = targets;
    }

    SystemFlagsDeployResult deployFlags(SystemFlagsDataArchive archive, boolean dryRun) {
        Map<FlagsTarget, Future<SystemFlagsDeployResult>> futures = new HashMap<>();
        for (FlagsTarget target : targets) {
            futures.put(target, executor.submit(() -> deployFlags(target, archive.flagData(target), dryRun)));
        }
        List<SystemFlagsDeployResult> results = new ArrayList<>();
        futures.forEach((target, future) -> {
            try {
                results.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                log.log(Level.SEVERE, Text.format("Failed to deploy flags for target '%s': %s", target, e.getMessage()), e);
                throw new RuntimeException(e);
            }
        });
        try {
            archive.validateAllFilesAreForTargets(system, targets);
        } catch (IllegalArgumentException e) {
            results.add(new SystemFlagsDeployResult(List.of(OperationError.archiveValidationFailed(e.getMessage()))));
        }
        return SystemFlagsDeployResult.merge(results);
    }

    private SystemFlagsDeployResult deployFlags(FlagsTarget target, List<FlagData> flagData, boolean dryRun) {
        Map<FlagId, FlagData> wantedFlagData = lookupTable(flagData);
        Map<FlagId, FlagData> currentFlagData;
        List<FlagId> definedFlags;
        try {
            currentFlagData = lookupTable(client.listFlagData(target));
            definedFlags = client.listDefinedFlags(target);
        } catch (Exception e) {
            log.log(Level.WARNING, Text.format("Failed to list flag data for target '%s': %s", target, e.getMessage()), e);
            return new SystemFlagsDeployResult(List.of(OperationError.listFailed(e.getMessage(), target)));
        }

        List<OperationError> errors = new ArrayList<>();
        List<FlagDataChange> results = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();

        createNewFlagData(target, dryRun, wantedFlagData, currentFlagData, results, errors);
        updateExistingFlagData(target, dryRun, wantedFlagData, currentFlagData, results, errors);
        removeOldFlagData(target, dryRun, wantedFlagData, currentFlagData, results, errors);
        failOnNewFlagDataForUndefinedFlags(target, wantedFlagData, currentFlagData, definedFlags, errors);
        failOnFlagDataForUndefinedFlags(target, wantedFlagData, currentFlagData, definedFlags, errors);
        return new SystemFlagsDeployResult(results, errors, warnings);
    }

    private void createNewFlagData(FlagsTarget target,
                                   boolean dryRun,
                                   Map<FlagId, FlagData> wantedFlagData,
                                   Map<FlagId, FlagData> currentFlagData,
                                   List<FlagDataChange> results,
                                   List<OperationError> errors) {
        wantedFlagData.forEach((id, data) -> {
            FlagData currentData = currentFlagData.get(id);
            if (currentData != null) {
                return; // not a new flag
            }
            try {
                if (!dryRun) {
                    client.putFlagData(target, data);
                } else {
                    dryRunFlagDataValidation(data);
                }
            } catch (Exception e) {
                log.log(Level.WARNING, Text.format("Failed to put flag '%s' for target '%s': %s", data.id(), target, e.getMessage()), e);
                errors.add(OperationError.createFailed(e.getMessage(), target, data));
                return;
            }
            results.add(FlagDataChange.created(id, target, data));
        });
    }

    private void updateExistingFlagData(FlagsTarget target,
                                        boolean dryRun,
                                        Map<FlagId, FlagData> wantedFlagData,
                                        Map<FlagId, FlagData> currentFlagData,
                                        List<FlagDataChange> results,
                                        List<OperationError> errors) {
        wantedFlagData.forEach((id, wantedData) -> {
            FlagData currentData = currentFlagData.get(id);
            if (currentData == null || isEqual(currentData, wantedData)) {
                return; // not an flag data update
            }
            try {
                if (!dryRun) {
                    client.putFlagData(target, wantedData);
                } else {
                    dryRunFlagDataValidation(wantedData);
                }
            } catch (Exception e) {
                log.log(Level.WARNING, Text.format("Failed to update flag '%s' for target '%s': %s", wantedData.id(), target, e.getMessage()), e);
                errors.add(OperationError.updateFailed(e.getMessage(), target, wantedData));
                return;
            }
            results.add(FlagDataChange.updated(id, target, wantedData, currentData));
        });
    }

    private void removeOldFlagData(FlagsTarget target,
                                   boolean dryRun,
                                   Map<FlagId, FlagData> wantedFlagData,
                                   Map<FlagId, FlagData> currentFlagData,
                                   List<FlagDataChange> results,
                                   List<OperationError> errors) {
        currentFlagData.forEach((id, data) -> {
            if (wantedFlagData.containsKey(id)) {
                return; // not a removed flag
            }
            if (!dryRun) {
                try {
                    client.deleteFlagData(target, id);
                } catch (Exception e) {
                    log.log(Level.WARNING, Text.format("Failed to delete flag '%s' for target '%s': %s", id, target, e.getMessage()), e);
                    errors.add(OperationError.deleteFailed(e.getMessage(), target, id));
                    return;
                }
            }
            results.add(FlagDataChange.deleted(id, target));
        });
    }

    private static void failOnNewFlagDataForUndefinedFlags(FlagsTarget target,
                                                           Map<FlagId, FlagData> wantedFlagData,
                                                           Map<FlagId, FlagData> currentFlagData,
                                                           List<FlagId> definedFlags,
                                                           List<OperationError> errors) {
        String errorMessage = "Flag not defined in target zone. If zone/configserver cluster is new, add an empty flag " +
                "data file for this zone as a temporary measure until the stale flag data files are removed.";
        for (FlagId flagId : wantedFlagData.keySet()) {
            if (!currentFlagData.containsKey(flagId) && !definedFlags.contains(flagId)) {
                errors.add(OperationError.createFailed(errorMessage, target, wantedFlagData.get(flagId)));
            }
        }
    }

    private static void failOnFlagDataForUndefinedFlags(FlagsTarget target,
                                                        Map<FlagId, FlagData> wantedFlagData,
                                                        Map<FlagId, FlagData> currentFlagData,
                                                        List<FlagId> definedFlags,
                                                        List<OperationError> errors) {
        for (FlagId flagId : currentFlagData.keySet()) {
            if (wantedFlagData.containsKey(flagId) && !definedFlags.contains(flagId)) {
                errors.add(OperationError.dataForUndefinedFlag(target, flagId));
            }
        }
    }

    private static void dryRunFlagDataValidation(FlagData data) {
        Flags.getFlag(data.id())
                .ifPresent(definition -> data.validate(definition.getUnboundFlag().serializer()));
    }

    private static Map<FlagId, FlagData> lookupTable(Collection<FlagData> data) {
        return data.stream().collect(Collectors.toMap(FlagData::id, Function.identity()));
    }

    private static boolean isEqual(FlagData l, FlagData r) {
        return Objects.equals(l.toJsonNode(), r.toJsonNode());
    }

}
