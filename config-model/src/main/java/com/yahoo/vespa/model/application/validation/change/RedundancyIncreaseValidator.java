// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import java.time.Instant;
import java.util.List;

/**
 * Checks that redundancy is not increased (without a validation override),
 * as that may easily cause the cluster to run out of reasources.
 *
 * @author bratseth
 */
public class RedundancyIncreaseValidator implements ChangeValidator {

    @Override
    public List<ConfigChangeAction> validate(VespaModel current, VespaModel next, ValidationOverrides overrides, Instant now) {
        for (ContentCluster currentCluster : current.getContentClusters().values()) {
            ContentCluster nextCluster = next.getContentClusters().get(currentCluster.getSubId());
            if (nextCluster == null) continue;
            if (redundancyOf(nextCluster) > redundancyOf(currentCluster)) {
                overrides.invalid(ValidationId.redundancyIncrease,
                                  "Increasing redundancy from " + redundancyOf(currentCluster) + " to " +
                                  redundancyOf(nextCluster) + " in '" + currentCluster + ". " +
                                  "This is a safe operation but verify that you have room for a " +
                                  redundancyOf(nextCluster) + "/" + redundancyOf(currentCluster) + "x increase " +
                                  "in content size",
                                  now);
            }
        }
        return List.of();
    }

    private int redundancyOf(ContentCluster cluster) {
        return cluster.redundancy().finalRedundancy();
    }

}
