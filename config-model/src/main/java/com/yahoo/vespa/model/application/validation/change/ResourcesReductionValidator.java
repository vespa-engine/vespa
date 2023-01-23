// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
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
public class ResourcesReductionValidator implements ChangeValidator {

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
        if (current.maxResources().nodeResources().isUnspecified() || next.maxResources().nodeResources().isUnspecified()) {
            // Unspecified resources; compared node count
            int currentNodes = current.maxResources().nodes();
            int nextNodes = next.maxResources().nodes();
            if (nextNodes < 0.5 * currentNodes && nextNodes != currentNodes - 1) {
                overrides.invalid(ValidationId.resourcesReduction,
                                  "Size reduction in '" + clusterId.value() + "' is too large: " +
                                  "To guard against mistakes, the new max nodes must be at least 50% of the current nodes. " +
                                  "Current nodes: " + currentNodes + ", new nodes: " + nextNodes,
                                  now);
            }
        }
        else {
            NodeResources currentResources = current.maxResources().totalResources();
            NodeResources nextResources = next.maxResources().totalResources();
            if (nextResources.vcpu() < 0.5 * currentResources.vcpu() ||
                nextResources.memoryGb() < 0.5 * currentResources.memoryGb() ||
                nextResources.diskGb() < 0.5 * currentResources.diskGb())
                overrides.invalid(ValidationId.resourcesReduction,
                                  "Resource reduction in '" + clusterId.value() + "' is too large: " +
                                  "To guard against mistakes, the new max resources must be at least 50% of the current " +
                                  "max resources in all dimensions. " +
                                  "Current: " + currentResources + ", new: " + nextResources,
                                  now);
        }
    }

}
