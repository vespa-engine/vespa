// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Returns any change to the indexing mode of a cluster.
 *
 * @author hmusum
 */
public class IndexingModeChangeValidator implements ChangeValidator {

    @Override
    public List<ConfigChangeAction> validate(VespaModel currentModel, VespaModel nextModel, 
                                             ValidationOverrides overrides, Instant now) {
        List<ConfigChangeAction> actions = new ArrayList<>();
        for (Map.Entry<String, ContentCluster> currentEntry : currentModel.getContentClusters().entrySet()) {
            ContentCluster nextCluster = nextModel.getContentClusters().get(currentEntry.getKey());
            if (nextCluster == null) continue;

            Optional<ConfigChangeAction> change = validateContentCluster(currentEntry.getValue(), nextCluster, overrides, now);
            if (change.isPresent())
                actions.add(change.get());
        }
        return actions;
    }

    private Optional<ConfigChangeAction> validateContentCluster(ContentCluster currentCluster, ContentCluster nextCluster,
                                                                ValidationOverrides overrides, Instant now) {
        boolean currentClusterIsIndexed = currentCluster.getSearch().hasIndexedCluster();
        boolean nextClusterIsIndexed = nextCluster.getSearch().hasIndexedCluster();

        if (currentClusterIsIndexed == nextClusterIsIndexed) return Optional.empty();

        return Optional.of(VespaReindexAction.of(currentCluster.id(),
                                                ValidationId.indexModeChange.value(),
                                                overrides,
                                                "Cluster '" + currentCluster.getName() + "' changed indexing mode from '" +
                                                indexingMode(currentClusterIsIndexed) + "' to '" + indexingMode(nextClusterIsIndexed) + "'",
                                                now));
    }

    private String indexingMode(boolean isIndexed) {
        return isIndexed ? "indexed" : "streaming";
    }

}
