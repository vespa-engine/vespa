// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

/**
 * Checks that this does not remove a data type in a cluster, as that causes deletion
 * of all data of that type.
 *
 * @author bratseth
 */
public class ContentTypeRemovalValidator implements ChangeValidator {

    @Override
    public void validate(ChangeContext context) {
        for (ContentCluster currentCluster : context.previousModel().getContentClusters().values()) {
            ContentCluster nextCluster = context.model().getContentClusters().get(currentCluster.getSubId());
            if (nextCluster == null) continue; // validated elsewhere

            for (NewDocumentType type : currentCluster.getDocumentDefinitions().values()) {
                if ( ! nextCluster.getDocumentDefinitions().containsKey(type.getName())) {
                    context.invalid(ValidationId.contentTypeRemoval,
                                    "Schema '" + type.getName() + "' is removed " +
                                    "in content cluster '" + currentCluster.getName() + "'. " +
                                    "This will cause loss of all data in this schema");
                }
            }
        }
    }

}
