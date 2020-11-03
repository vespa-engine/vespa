// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.ContentSearchCluster;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.search.SearchNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;

/**
 * Returns any change to the indexing mode of a cluster.
 *
 * @author hmusum
 * @author bjorncs
 */
public class IndexingModeChangeValidator implements ChangeValidator {

    @Override
    public List<ConfigChangeAction> validate(VespaModel currentModel, VespaModel nextModel, 
                                             ValidationOverrides overrides, Instant now) {
        List<ConfigChangeAction> actions = new ArrayList<>();
        for (Map.Entry<String, ContentCluster> currentEntry : currentModel.getContentClusters().entrySet()) {
            ContentCluster nextCluster = nextModel.getContentClusters().get(currentEntry.getKey());
            if (nextCluster == null) continue;
            actions.addAll(validateContentCluster(currentEntry.getValue(), nextCluster, overrides, now));
        }
        return actions;
    }

    private List<ConfigChangeAction> validateContentCluster(
            ContentCluster currentCluster, ContentCluster nextCluster, ValidationOverrides overrides, Instant now) {
        List<ConfigChangeAction> changes = new ArrayList<>();
        ContentSearchCluster currentSearchCluster = currentCluster.getSearch();
        ContentSearchCluster nextSearchCluster = nextCluster.getSearch();
        {
            Set<String> currentStreamingTypes = toDocumentTypeNames(currentSearchCluster.getDocumentTypesWithStreamingCluster());
            Set<String> nextIndexedTypes = toDocumentTypeNames(nextSearchCluster.getDocumentTypesWithIndexedCluster());
            for (String type : nextIndexedTypes) {
                if (currentStreamingTypes.contains(type)) {
                    changes.add(createReindexAction(overrides, now, nextCluster, type, "streaming", "indexed"));
                }
            }
        }
        {
            Set<String> currentIndexedTypes = toDocumentTypeNames(currentSearchCluster.getDocumentTypesWithIndexedCluster());
            Set<String> nextStreamingTypes = toDocumentTypeNames(nextSearchCluster.getDocumentTypesWithStreamingCluster());
            for (String type : nextStreamingTypes) {
                if (currentIndexedTypes.contains(type)) {
                    changes.add(createReindexAction(overrides, now, nextCluster, type, "indexed", "streaming"));
                }
            }
        }
        return changes;
    }

    private static VespaReindexAction createReindexAction(
            ValidationOverrides overrides, Instant now, ContentCluster nextCluster, String documentType, String indexModeFrom, String indexModeTo) {
        List<ServiceInfo> services = nextCluster.getSearch().getSearchNodes().stream()
                .map(SearchNode::getServiceInfo)
                .collect(Collectors.toList());
        return VespaReindexAction.of(
                nextCluster.id(),
                ValidationId.indexModeChange.value(),
                overrides,
                String.format("Document type '%s' in cluster '%s' changed indexing mode from '%s' to '%s'", documentType, nextCluster.getName(), indexModeFrom, indexModeTo),
                services,
                documentType,
                now);
    }

    private static Set<String> toDocumentTypeNames(List<NewDocumentType> types) {
        return types.stream()
                .map(type -> type.getFullName().getName())
                .collect(toSet());
    }

}
