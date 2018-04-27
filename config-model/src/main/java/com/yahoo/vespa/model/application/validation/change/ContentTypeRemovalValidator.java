// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Checks that this does not remove a data type in a cluster, as that causes deletion
 * of all data of that type.
 *
 * @author bratseth
 */
public class ContentTypeRemovalValidator implements ChangeValidator {

    @Override
    public List<ConfigChangeAction> validate(VespaModel current, VespaModel next, ValidationOverrides overrides, Instant now) {
        for (ContentCluster currentCluster : current.getContentClusters().values()) {
            ContentCluster nextCluster = next.getContentClusters().get(currentCluster.getSubId());
            if (nextCluster == null) continue; // validated elsewhere

            for (NewDocumentType type : currentCluster.getDocumentDefinitions().values()) {
                if ( ! nextCluster.getDocumentDefinitions().containsKey(type.getName())) {
                    overrides.invalid(ValidationId.contentTypeRemoval,
                                      "Type '" + type.getName() + "' is removed " +
                                      " in content cluster '" + currentCluster.getName() + "'. " +
                                      "This will cause loss of all data of this type",
                                      now);
                }
            }
        }
        return Collections.emptyList();
    }

}
