// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.collections.Pair;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.VespaModel;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Checks that no nodes resources are reduced too much in one go.
 *
 * @author freva
 */
public class ResourcesReductionValidator implements ChangeValidator {

    @Override
    public List<ConfigChangeAction> validate(VespaModel current, VespaModel next, ValidationOverrides overrides, Instant now) {
        var currentRequestedResourcesByClusterId = getRequestedResourcesByClusterId(current);
        var nextRequestedResourcesByClusterId = getRequestedResourcesByClusterId(next);

        for (var clusterTypeAndId : currentRequestedResourcesByClusterId.keySet()) {
            if (!nextRequestedResourcesByClusterId.containsKey(clusterTypeAndId)) continue;
            validate(currentRequestedResourcesByClusterId.get(clusterTypeAndId),
                     nextRequestedResourcesByClusterId.get(clusterTypeAndId),
                     clusterTypeAndId.getSecond(),
                     overrides,
                     now);
        }

        return List.of();
    }

    private void validate(NodeResources currentResources, NodeResources nextResources, ClusterSpec.Id clusterId,
                          ValidationOverrides overrides, Instant now) {
        List<String> illegalChanges = Stream.of(
                validateResource("vCPU", currentResources.vcpu(), nextResources.vcpu()),
                validateResource("memory GB", currentResources.memoryGb(), nextResources.memoryGb()),
                validateResource("disk GB", currentResources.diskGb(), nextResources.diskGb()))
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
        if (illegalChanges.isEmpty()) return;

        overrides.invalid(ValidationId.resourcesReduction,
                "Resource reduction in '" + clusterId.value() + "' is too large. " +
                String.join(" ", illegalChanges) + " New resources must be at least 50% of the current resources",
                now);
    }

    private static Optional<String> validateResource(String resourceName, double currentValue, double nextValue) {
        // don't allow more than 50% reduction, but always allow to reduce by 1
        if (nextValue >= currentValue * 0.5 || nextValue >= currentValue - 1) return Optional.empty();
        return Optional.of(String.format("Current %s: %.2f, new: %.2f.", resourceName, currentValue, nextValue));
    }

    private static Map<Pair<ClusterSpec.Type, ClusterSpec.Id>, NodeResources> getRequestedResourcesByClusterId(VespaModel vespaModel) {
        return vespaModel.hostSystem().getHosts().stream()
                .map(HostResource::spec)
                .filter(spec -> spec.membership().isPresent() && spec.requestedResources().isPresent())
                .filter(spec -> !spec.membership().get().retired())
                .collect(Collectors.toMap(
                        spec -> new Pair<>(spec.membership().get().cluster().type(), spec.membership().get().cluster().id()),
                        spec -> spec.requestedResources().get(),
                        (e1, e2) -> e1));
    }

}
