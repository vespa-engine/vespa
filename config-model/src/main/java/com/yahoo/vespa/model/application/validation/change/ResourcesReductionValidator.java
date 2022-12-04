// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.VespaModel;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
        for (var clusterId : current.allClusters()) {
            Capacity currentCapacity = current.provisioned().all().get(clusterId);
            Capacity nextCapacity = next.provisioned().all().get(clusterId);
            if (currentCapacity == null || nextCapacity == null) continue;
            validate(currentCapacity, nextCapacity, clusterId, overrides, now);
        }

        return List.of();
    }

    private void validate(Capacity current,
                          Capacity next,
                          ClusterSpec.Id clusterId,
                          ValidationOverrides overrides,
                          Instant now) {
        if (current.minResources().nodeResources().isUnspecified()) return;
        if (next.minResources().nodeResources().isUnspecified()) return;

        List<String> illegalChanges = Stream.of(
                validateResource("vCPU",
                                 current.minResources().nodeResources().vcpu(),
                                 next.minResources().nodeResources().vcpu()),
                validateResource("memory GB",
                                 current.minResources().nodeResources().memoryGb(),
                                 next.minResources().nodeResources().memoryGb()),
                validateResource("disk GB",
                                 current.minResources().nodeResources().diskGb(),
                                 next.minResources().nodeResources().diskGb()))
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
        if (illegalChanges.isEmpty()) return;

        overrides.invalid(ValidationId.resourcesReduction,
                          "Resource reduction in '" + clusterId.value() + "' is too large. " +
                          String.join(" ", illegalChanges) +
                          " New min resources must be at least 50% of the current min resources",
                          now);
    }

    private static Optional<String> validateResource(String resourceName, double currentValue, double nextValue) {
        // don't allow more than 50% reduction, but always allow to reduce by 1
        if (nextValue >= currentValue * 0.5 || nextValue >= currentValue - 1) return Optional.empty();
        return Optional.of(String.format(Locale.ENGLISH ,"Current %s: %.2f, new: %.2f.", resourceName, currentValue, nextValue));
    }

}
