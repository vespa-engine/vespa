// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.systemflags;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.FlagsTarget;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.SystemFlagsDataArchive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.restapi.systemflags.SystemFlagsDeployResult.FlagDataChange;

/**
 * Deploy a flags data archive to all targets in a given system
 *
 * @author bjorncs
 */
class SystemFlagsDeployer  {

    private final FlagsClient client;
    private final Set<FlagsTarget> targets;
    private final ExecutorCompletionService<SystemFlagsDeployResult> completionService =
            new ExecutorCompletionService<>(Executors.newCachedThreadPool(new DaemonThreadFactory("system-flags-deployer-")));


    SystemFlagsDeployer(ServiceIdentityProvider identityProvider, Set<FlagsTarget> targets) {
        this(new FlagsClient(identityProvider, targets), targets);
    }

    SystemFlagsDeployer(FlagsClient client, Set<FlagsTarget> targets) {
        this.client = client;
        this.targets = targets;
    }

    SystemFlagsDeployResult deployFlags(SystemFlagsDataArchive archive, boolean dryRun) {
        for (FlagsTarget target : targets) {
            completionService.submit(() -> deployFlags(target, archive.flagData(target), dryRun));
        }
        List<SystemFlagsDeployResult> results = new ArrayList<>();
        Future<SystemFlagsDeployResult> future;
        try {
            while (results.size() < targets.size() && (future = completionService.take()) != null) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    // TODO Handle errors
                    throw new RuntimeException(e);
                }
            }
            return SystemFlagsDeployResult.merge(results);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // TODO Handle http status code 4xx/5xx (e.g for unknown flag id)
    private SystemFlagsDeployResult deployFlags(FlagsTarget target, Set<FlagData> flagData, boolean dryRun) {
        Map<FlagId, FlagData> wantedFlagData = lookupTable(flagData);
        Map<FlagId, FlagData> currentFlagData = lookupTable(client.listFlagData(target));

        List<FlagDataChange> result = new ArrayList<>();

        wantedFlagData.forEach((id, data) -> {
            FlagData currentData = currentFlagData.get(id);
            if (currentData != null && Objects.equals(currentData.toJsonNode(), data.toJsonNode())) {
                return; // new flag data identical to existing
            }
            if (!dryRun) {
                client.putFlagData(target, data);
            }
            result.add(
                    currentData != null
                            ? FlagDataChange.updated(id, Set.of(target), data, currentData)
                            : FlagDataChange.created(id, Set.of(target), data));
        });

        currentFlagData.forEach((id, data) -> {
            if (!wantedFlagData.containsKey(id)) {
                if (!dryRun) {
                    client.deleteFlagData(target, id);
                }
                result.add(FlagDataChange.deleted(id, Set.of(target)));
            }
        });

        return new SystemFlagsDeployResult(result);
    }

    private static Map<FlagId, FlagData> lookupTable(Collection<FlagData> data) {
        return data.stream().collect(Collectors.toMap(FlagData::id, Function.identity()));
    }

}
