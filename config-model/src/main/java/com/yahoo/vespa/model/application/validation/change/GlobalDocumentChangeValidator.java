// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import java.util.Collections;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Class that fails via exception if global attribute changes for a document
 * type in a content cluster unless corresponding override is present.
 */
public class GlobalDocumentChangeValidator implements ChangeValidator {

    @Override
    public List<ConfigChangeAction> validate(VespaModel currentModel, VespaModel nextModel,
                                             ValidationOverrides overrides, Instant now) {
        if (!overrides.allows(ValidationId.globalDocumentChange.value(), now)) {
            for (Map.Entry<String, ContentCluster> currentEntry : currentModel.getContentClusters().entrySet()) {
                ContentCluster nextCluster = nextModel.getContentClusters().get(currentEntry.getKey());
                if (nextCluster == null) continue;

                validateContentCluster(currentEntry.getValue(), nextCluster);
            }
        }
        return Collections.emptyList();
    }

    private void validateContentCluster(ContentCluster currentCluster, ContentCluster nextCluster) {
        String clusterName = currentCluster.getName();
        currentCluster.getDocumentDefinitions().forEach((documentTypeName, currentDocumentType) -> {
            NewDocumentType nextDocumentType = nextCluster.getDocumentDefinitions().get(documentTypeName);
            if (nextDocumentType != null) {
                boolean currentIsGlobal = currentCluster.isGloballyDistributed(currentDocumentType);
                boolean nextIsGlobal = nextCluster.isGloballyDistributed(nextDocumentType);
                if (currentIsGlobal != nextIsGlobal) {
                    throw new IllegalStateException(String.format("Document type %s in cluster %s changed global from %s to %s",
                            documentTypeName, clusterName, currentIsGlobal, nextIsGlobal));
                }
            }
        });
    }
}

