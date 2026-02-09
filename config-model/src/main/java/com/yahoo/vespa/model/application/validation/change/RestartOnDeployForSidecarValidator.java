// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.SidecarSpec;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Sets restartOnDeploy for existing clusters when adding, changing or removing sidecars.
 * This ensures that Vespa components are reconstructed given updated sidecar containers.
 *
 * @author glebashnik
 */
public class RestartOnDeployForSidecarValidator implements ChangeValidator {

    @Override
    public void validate(ChangeContext context) {
        // Validate sidecars in existing clusters only, new clusters do not need restartOnDeploy.
        for (var previousCluster : context.previousModel().getContainerClusters().values()) {
            var nextCluster = context.model().getContainerClusters().get(previousCluster.name());

            if (nextCluster == null) {
                continue;
            }

            var clusterId = previousCluster.id();

            var previousClusterSpec = findClusterSpec(context.previousModel(), clusterId);
            if (previousClusterSpec.isEmpty()) {
                continue;
            }

            var nextClusterSpec = findClusterSpec(context.model(), clusterId);
            if (nextClusterSpec.isEmpty()) {
                continue;
            }

            var previousSidecars = previousClusterSpec.get().sidecars();
            var nextSidecars = nextClusterSpec.get().sidecars();

            var removedSidecars = previousSidecars.stream().filter(previousSidecar -> nextSidecars.stream().noneMatch(
                    sidecar -> sidecar.matchesByIdOrName(previousSidecar))).toList();

            var addedSidecars = nextSidecars.stream().filter(nextSidecar -> previousSidecars.stream().noneMatch(
                    sidecar -> sidecar.matchesByIdOrName(nextSidecar))).toList();

            var changedSidecars = nextSidecars.stream().filter(nextSidecar -> previousSidecars.stream().anyMatch(
                    sidecar -> sidecar.matchesByIdOrName(nextSidecar) && !sidecar.equals(nextSidecar))).toList();

            var messageBuilder = new StringBuilder(String.format(java.util.Locale.ROOT, "Need to restart services in %s due to", clusterId));

            if (!removedSidecars.isEmpty()) {
                messageBuilder.append(" removed sidecars: ").append(joinSidecarNames(removedSidecars));
            }

            if (!addedSidecars.isEmpty()) {
                if (!removedSidecars.isEmpty()) {
                    messageBuilder.append("; ");
                }
                messageBuilder.append(" added sidecars: ").append(joinSidecarNames(addedSidecars));
            }

            if (!changedSidecars.isEmpty()) {
                if (!removedSidecars.isEmpty() || !addedSidecars.isEmpty()) {
                    messageBuilder.append("; ");
                }

                var changedSidecarsMessage = changedSidecars.stream().map(changedSidecar -> {
                    var matchingPreviousSidecar = previousSidecars.stream().filter(sidecar -> sidecar.matchesByIdOrName(
                            changedSidecar)).findFirst().orElseThrow(); // Should never throw.

                    var sidecarDiff = diffSidecarSpecs(matchingPreviousSidecar, changedSidecar);
                    return String.format(java.util.Locale.ROOT, "'%s' (%s)", changedSidecar.name(), sidecarDiff);
                }).collect(Collectors.joining(", "));

                messageBuilder.append(" changed sidecars: ").append(changedSidecarsMessage);
            }

            if (!removedSidecars.isEmpty() || !addedSidecars.isEmpty() || !changedSidecars.isEmpty()) {
                addRestartAction(context, nextCluster, messageBuilder.toString());
            }
        }
    }

    private Optional<ClusterSpec> findClusterSpec(VespaModel model, ClusterSpec.Id clusterId) {
        return model.allClusters().stream().filter(c -> c.id().equals(clusterId)).findFirst();
    }

    private String joinSidecarNames(List<SidecarSpec> sidecars) {
        return sidecars.stream().map(sidecar -> String.format(java.util.Locale.ROOT, "'%s'", sidecar.name())).collect(Collectors.joining(", "));
    }

    private String diffSidecarSpecs(SidecarSpec from, SidecarSpec to) {
        var changes = new ArrayList<String>();
        var fromResources = from.resources();
        var toResources = to.resources();

        if (from.id() != to.id()) {
            changes.add(String.format(java.util.Locale.ROOT, "id: %s -> %s", from.id(), to.id()));
        }

        if (!from.name().equals(to.name())) {
            changes.add(String.format(java.util.Locale.ROOT, "name: %s -> %s", from.name(), to.name()));
        }

        if (!from.image().equals(to.image())) {
            changes.add(String.format(java.util.Locale.ROOT, "image: %s -> %s", from.image(), to.image()));
        }

        if (fromResources.maxCpu() != toResources.maxCpu()) {
            changes.add(String.format(java.util.Locale.ROOT, "maxCpu: %s -> %s", fromResources.maxCpu(), toResources.maxCpu()));
        }

        if (fromResources.minCpu() != toResources.minCpu()) {
            changes.add(String.format(java.util.Locale.ROOT, "minCpu: %s -> %s", fromResources.minCpu(), toResources.minCpu()));
        }

        if (fromResources.memoryGiB() != toResources.memoryGiB()) {
            changes.add(String.format(java.util.Locale.ROOT, "memoryGiB: %s -> %s", fromResources.memoryGiB(), toResources.memoryGiB()));
        }

        if (fromResources.hasGpu() != toResources.hasGpu()) {
            changes.add(String.format(java.util.Locale.ROOT, "hasGpu: %s -> %s", fromResources.hasGpu(), toResources.hasGpu()));
        }

        if (!from.volumeMounts().equals(to.volumeMounts())) {
            changes.add(String.format(java.util.Locale.ROOT, "volumeMounts: %s -> %s", from.volumeMounts(), to.volumeMounts()));
        }

        if (!from.envs().equals(to.envs())) {
            changes.add(String.format(java.util.Locale.ROOT, "envs: %s -> %s", from.envs(), to.envs()));
        }

        if (!from.command().equals(to.command())) {
            changes.add(String.format(java.util.Locale.ROOT, "command: %s -> %s", from.command(), to.command()));
        }

        // Skipping livenessProbe diff since it doesn't affect sidecar functionality from sidecar client perspective.

        return String.join(", ", changes);
    }

    private void addRestartAction(ChangeContext context, ApplicationContainerCluster cluster, String message) {
        var services = cluster.getContainers().stream().map(AbstractService::getServiceInfo).toList();

        context.require(new VespaRestartAction(
                cluster.id(), message, services,
                VespaRestartAction.ConfigChange.DEFER_UNTIL_RESTART
        ));
    }
}
