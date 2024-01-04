// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import java.util.Map;

/**
 * Class that adds a validation failure if global attribute changes for a document
 * type in a content cluster unless corresponding override is present.
 */
public class GlobalDocumentChangeValidator implements ChangeValidator {

    @Override
    public void validate(ChangeContext context) {
        // TODO: cause restarts
        if (!context.deployState().validationOverrides().allows(ValidationId.globalDocumentChange, context.deployState().now())) {
            for (Map.Entry<String, ContentCluster> currentEntry : context.previousModel().getContentClusters().entrySet()) {
                ContentCluster nextCluster = context.model().getContentClusters().get(currentEntry.getKey());
                if (nextCluster == null) continue;

                validateContentCluster(context, currentEntry.getValue(), nextCluster);
            }
        }
    }

    private void validateContentCluster(ChangeContext context, ContentCluster currentCluster, ContentCluster nextCluster) {
        String clusterName = currentCluster.getName();
        currentCluster.getDocumentDefinitions().forEach((documentTypeName, currentDocumentType) -> {
            NewDocumentType nextDocumentType = nextCluster.getDocumentDefinitions().get(documentTypeName);
            if (nextDocumentType != null) {
                boolean currentIsGlobal = currentCluster.isGloballyDistributed(currentDocumentType);
                boolean nextIsGlobal = nextCluster.isGloballyDistributed(nextDocumentType);
                if (currentIsGlobal != nextIsGlobal) {
                    context.illegal(String.format("Document type %s in cluster %s changed global from %s to %s. " +
                                                  "Add validation override '%s' to force this change through. " +
                                                  "First, stop services on all content nodes. Then, deploy with validation override. Finally, start services on all content nodes.",
                            documentTypeName, clusterName, currentIsGlobal, nextIsGlobal, ValidationId.globalDocumentChange.value()));
                }
            }
        });
    }
}

