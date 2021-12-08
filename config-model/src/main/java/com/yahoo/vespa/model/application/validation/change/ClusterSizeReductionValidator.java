// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;

import java.time.Instant;
import java.util.List;

/**
 * Checks that no cluster sizes are reduced too much in one go.
 *
 * @author bratseth
 */
public class ClusterSizeReductionValidator implements ChangeValidator {

    @Override
    public List<ConfigChangeAction> validate(VespaModel current, VespaModel next, ValidationOverrides overrides, Instant now) {
        for (var clusterId : current.allClusters()) {
            Capacity currentCapacity = current.provisioned().all().get(clusterId);
            Capacity nextCapacity = next.provisioned().all().get(clusterId);
            if (currentCapacity == null || nextCapacity == null) continue;
            validate(currentCapacity,
                     nextCapacity,
                     clusterId,
                     overrides,
                     now);
        }
        return List.of();
    }

    private void validate(Capacity current,
                          Capacity next,
                          ClusterSpec.Id clusterId,
                          ValidationOverrides overrides,
                          Instant now) {
        int currentSize = current.minResources().nodes();
        int nextSize = next.minResources().nodes();
        // don't allow more than 50% reduction, but always allow to reduce size with 1
        if ( nextSize < currentSize * 0.5 && nextSize != currentSize - 1)
            overrides.invalid(ValidationId.clusterSizeReduction,
                              "Size reduction in '" + clusterId.value() + "' is too large: " +
                              "New min size must be at least 50% of the current min size. " +
                              "Current size: " + currentSize + ", new size: " + nextSize,
                              now);
    }

}
