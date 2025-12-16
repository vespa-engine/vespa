// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.SidecarSpec;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;

import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Sets restartOnDeploy when a sidecar is added or changed to an existing cluster.
 * Adding a sidecar to a new cluster does not require a restartOnDeploy.
 * Removing sidecar from an existing cluster does not require a restartOnDeploy.
 *
 * @author glebashnik
 */
public class RestartOnDeployForSidecarValidator implements ChangeValidator {

    @Override
    public void validate(ChangeContext context) {
        for (var previousCluster : context.previousModel().getContainerClusters().values()) {
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

            for (var nextSidecar : nextSidecars) {
                var matchingPreviousSidecar = previousSidecars.stream()
                        .filter(sidecar -> sidecar.matchesByIdOrName(nextSidecar))
                        .findFirst();

                if (matchingPreviousSidecar.isEmpty()) {
                    var message = "Need to restart services in %s due to added sidecar '%s'"
                            .formatted(clusterId, nextSidecar.name());
                    addRestartAction(context, clusterId, message);
                } else {
                    var previousSidecar = matchingPreviousSidecar.get();

                    if (!previousSidecar.equals(nextSidecar)) {
                        var sidecarChanges = findSidecarChanges(previousSidecar, nextSidecar);
                        var restartMessage = "Need to restart services in %s due to changed sidecar '%s' (%s)"
                                .formatted(clusterId, nextSidecar.name(), sidecarChanges);
                        addRestartAction(context, clusterId, restartMessage);
                    }
                }
            }
        }
    }

    private Optional<ClusterSpec> findClusterSpec(VespaModel model, ClusterSpec.Id clusterId) {
        return model.allClusters().stream()
                .filter(c -> c.id().equals(clusterId))
                .findFirst();
    }

    private String findSidecarChanges(SidecarSpec from, SidecarSpec to) {
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

    private void addRestartAction(ChangeContext context, ClusterSpec.Id clusterId, String message) {
        context.require(new VespaRestartAction(
                clusterId,
                message,
                context.model().getContainerClusters().get(clusterId.value()).getContainers().stream()
                        .map(AbstractService::getServiceInfo)
                        .collect(Collectors.toList())));
    }
}
