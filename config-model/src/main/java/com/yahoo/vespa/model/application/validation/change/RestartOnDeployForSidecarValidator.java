// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.SidecarSpec;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;

import java.util.ArrayList;
import java.util.Optional;

/**
 * This validator sets restartOnDeploy for clusters with added, changed or removed sidecars.
 * This ensures that sidecar client components are created or removed after sidecar containers have been started or stopped.
 *
 * @author glebashnik
 */
public class RestartOnDeployForSidecarValidator implements ChangeValidator {

    @Override
    public void validate(ChangeContext context) {
        // Only validate sidecars for existing clusters.
        // New clusters with sidecars do not require restartOnDeploy.
        for (var previousCluster :
                context.previousModel().getContainerClusters().values()) {
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

            var removedSidecars = previousSidecars.stream()
                    .filter(previousSidecar ->
                            nextSidecars.stream().noneMatch(sidecar -> sidecar.matchesByIdOrName(previousSidecar)))
                    .toList();

            var addedSidecars = nextSidecars.stream()
                    .filter(nextSidecar ->
                            previousSidecars.stream().noneMatch(sidecar -> sidecar.matchesByIdOrName(nextSidecar)))
                    .toList();

            var changedSidecars = nextSidecars.stream()
                    .filter(nextSidecar -> previousSidecars.stream()
                            .anyMatch(
                                    sidecar -> sidecar.matchesByIdOrName(nextSidecar) && !sidecar.equals(nextSidecar)))
                    .toList();

            for (var removedSidecar : removedSidecars) {
                var message = "Need to restart services in %s due to removed sidecar '%s'"
                        .formatted(clusterId, removedSidecar.name());
                addRestartAction(context, nextCluster, message);
            }

            for (var addedSidecar : addedSidecars) {
                var message = "Need to restart services in %s due to added sidecar '%s'"
                        .formatted(clusterId, addedSidecar.name());
                addRestartAction(context, nextCluster, message);
            }

            for (var changedSidecar : changedSidecars) {
                var matchingPreviousSidecar = previousSidecars.stream()
                        .filter(sidecar -> sidecar.matchesByIdOrName(changedSidecar))
                        .findFirst()
                        .orElseThrow(); // Should never throw.

                var sidecarDiff = createSidecarDiffString(matchingPreviousSidecar, changedSidecar);
                var message = "Need to restart services in %s due to changed sidecar '%s' (%s)"
                        .formatted(clusterId, changedSidecar.name(), sidecarDiff);
                addRestartAction(context, nextCluster, message);
            }
        }
    }

    private Optional<ClusterSpec> findClusterSpec(VespaModel model, ClusterSpec.Id clusterId) {
        return model.allClusters().stream()
                .filter(c -> c.id().equals(clusterId))
                .findFirst();
    }

    private String createSidecarDiffString(SidecarSpec from, SidecarSpec to) {
        var changes = new ArrayList<String>();
        var fromResources = from.resources();
        var toResources = to.resources();

        if (from.id() != to.id()) {
            changes.add("id: " + from.id() + " -> " + to.id());
        }
        if (!from.name().equals(to.name())) {
            changes.add("name: '" + from.name() + "' -> '" + to.name() + "'");
        }
        if (!from.image().equals(to.image())) {
            changes.add("image: '" + from.image() + "' -> '" + to.image() + "'");
        }

        if (fromResources.maxCpu() != toResources.maxCpu()) {
            changes.add("maxCpu: " + fromResources.maxCpu() + " -> " + toResources.maxCpu());
        }
        if (fromResources.minCpu() != toResources.minCpu()) {
            changes.add("minCpu: " + fromResources.minCpu() + " -> " + toResources.minCpu());
        }
        if (fromResources.memoryGiB() != toResources.memoryGiB()) {
            changes.add("memoryGiB: " + fromResources.memoryGiB() + " -> " + toResources.memoryGiB());
        }
        if (fromResources.hasGpu() != toResources.hasGpu()) {
            changes.add("hasGpu: " + fromResources.hasGpu() + " -> " + toResources.hasGpu());
        }

        if (!from.volumeMounts().equals(to.volumeMounts())) {
            changes.add("volumeMounts: " + from.volumeMounts() + " -> " + to.volumeMounts());
        }
        if (!from.envs().equals(to.envs())) {
            changes.add("envs: " + from.envs() + " -> " + to.envs());
        }
        if (!from.command().equals(to.command())) {
            changes.add("command: " + from.command() + " -> " + to.command());
        }

        return String.join(", ", changes);
    }

    private void addRestartAction(ChangeContext context, ApplicationContainerCluster cluster, String message) {
        var services = cluster.getContainers().stream()
                .map(AbstractService::getServiceInfo)
                .toList();

        context.require(new VespaRestartAction(
                cluster.id(), message, services, VespaRestartAction.ConfigChange.DEFER_UNTIL_RESTART));
    }
}
