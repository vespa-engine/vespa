// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

/**
 * Checks that redundancy is not increased (without a validation override),
 * as that may easily cause the cluster to run out of resources.
 *
 * @author bratseth
 */
public class RedundancyIncreaseValidator implements ChangeValidator {

    @Override
    public void validate(ChangeContext context) {
        for (ContentCluster currentCluster : context.previousModel().getContentClusters().values()) {
            ContentCluster nextCluster = context.model().getContentClusters().get(currentCluster.getSubId());
            if (nextCluster == null) continue;
            if (redundancyOf(nextCluster) > redundancyOf(currentCluster)) {
                context.invalid(ValidationId.redundancyIncrease,
                                "Increasing redundancy from " + redundancyOf(currentCluster) + " to " +
                                redundancyOf(nextCluster) + " in '" + currentCluster + ". " +
                                "This is a safe operation but verify that you have room for a " +
                                redundancyOf(nextCluster) + "/" + redundancyOf(currentCluster) + "x increase " +
                                "in content size");
            }
        }
    }

    private int redundancyOf(ContentCluster cluster) { return cluster.getRedundancy().effectiveFinalRedundancy(); }

}
