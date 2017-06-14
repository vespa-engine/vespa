// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationId;
import com.yahoo.vespa.model.application.validation.ValidationOverrides;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import java.util.Collections;
import java.util.List;

/**
 * Checks that no cluster sizes are reduced too much in one go.
 *
 * @author bratseth
 */
public class ClusterSizeReductionValidator implements ChangeValidator {

    @Override
    public List<ConfigChangeAction> validate(VespaModel current, VespaModel next, ValidationOverrides overrides) {
        for (ContainerCluster currentCluster : current.getContainerClusters().values()) {
            ContainerCluster nextCluster = next.getContainerClusters().get(currentCluster.getName());
            if (nextCluster == null) continue;
            validate(currentCluster.getContainers().size(),
                     nextCluster.getContainers().size(),
                     currentCluster.getName(),
                     overrides);
        }

        for (ContentCluster currentCluster : current.getContentClusters().values()) {
            ContentCluster nextCluster = next.getContentClusters().get(currentCluster.getName());
            if (nextCluster == null) continue;
            validate(currentCluster.getSearch().getSearchNodes().size(),
                     nextCluster.getSearch().getSearchNodes().size(),
                     currentCluster.getName(),
                     overrides);
        }

        return Collections.emptyList();
    }

    private void validate(int currentSize, int nextSize, String clusterName, ValidationOverrides overrides) {
        // don't allow more than 50% reduction, but always allow to reduce size with 1
        if ( nextSize < ((double)currentSize) * 0.5 && nextSize != currentSize - 1)
            overrides.invalid(ValidationId.clusterSizeReduction,
                              "Size reduction in '" + clusterName + "' is too large. Current size: " + currentSize +
                              ", new size: " + nextSize + ". New size must be at least 50% of the current size");
    }

}
