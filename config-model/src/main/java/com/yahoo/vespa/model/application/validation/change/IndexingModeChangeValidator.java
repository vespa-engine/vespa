// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
            actions.addAll(validateContentCluster(currentEntry.getValue(), nextCluster));
        }
        return actions;
    }

    private static List<ConfigChangeAction> validateContentCluster(ContentCluster currentCluster, ContentCluster nextCluster) {
        List<ConfigChangeAction> actions = new ArrayList<>();
        ContentSearchCluster currentSearchCluster = currentCluster.getSearch();
        ContentSearchCluster nextSearchCluster = nextCluster.getSearch();
        findDocumentTypesWithActionableIndexingModeChange(
                actions, nextCluster,
                toDocumentTypeNames(currentSearchCluster.getDocumentTypesWithStreamingCluster()),
                toDocumentTypeNames(nextSearchCluster.getDocumentTypesWithIndexedCluster()),
                "streaming", "indexed");
        findDocumentTypesWithActionableIndexingModeChange(
                actions, nextCluster,
                toDocumentTypeNames(currentSearchCluster.getDocumentTypesWithIndexedCluster()),
                toDocumentTypeNames(nextSearchCluster.getDocumentTypesWithStreamingCluster()),
                "indexed", "streaming");
        findDocumentTypesWithActionableIndexingModeChange(
                actions, nextCluster,
                toDocumentTypeNames(currentSearchCluster.getDocumentTypesWithStoreOnly()),
                toDocumentTypeNames(nextSearchCluster.getDocumentTypesWithIndexedCluster()),
                "store-only", "indexed");
        findDocumentTypesWithActionableIndexingModeChange(
                actions, nextCluster,
                toDocumentTypeNames(currentSearchCluster.getDocumentTypesWithIndexedCluster()),
                toDocumentTypeNames(nextSearchCluster.getDocumentTypesWithStoreOnly()),
                "indexed", "store-only");
        return actions;
    }

    private static void findDocumentTypesWithActionableIndexingModeChange(
            List<ConfigChangeAction> actions, ContentCluster nextCluster,
            Set<String> currentTypes, Set<String> nextTypes, String currentIndexMode, String nextIndexingMode) {
        for (String type : nextTypes) {
            if (currentTypes.contains(type)) {
                List<ServiceInfo> services = nextCluster.getSearch().getSearchNodes().stream()
                        .map(SearchNode::getServiceInfo)
                        .collect(Collectors.toList());
                actions.add(VespaReindexAction.of(
                        nextCluster.id(),
                        ValidationId.indexModeChange,
                        String.format(
                                "Document type '%s' in cluster '%s' changed indexing mode from '%s' to '%s'",
                                type, nextCluster.getName(), currentIndexMode, nextIndexingMode),
                        services,
                        type
                ));
            }
        }
    }

    private static Set<String> toDocumentTypeNames(List<NewDocumentType> types) {
        return types.stream()
                .map(type -> type.getFullName().getName())
                .collect(Collectors.toCollection(() -> new LinkedHashSet<>()));
    }

}
