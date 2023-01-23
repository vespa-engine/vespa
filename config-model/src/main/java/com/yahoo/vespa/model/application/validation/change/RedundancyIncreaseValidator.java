// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import java.util.List;

/**
 * Checks that redundancy is not increased (without a validation override),
 * as that may easily cause the cluster to run out of resources.
 *
 * @author bratseth
 */
public class RedundancyIncreaseValidator implements ChangeValidator {

    @Override
    public List<ConfigChangeAction> validate(VespaModel current, VespaModel next, DeployState deployState) {
        for (ContentCluster currentCluster : current.getContentClusters().values()) {
            ContentCluster nextCluster = next.getContentClusters().get(currentCluster.getSubId());
            if (nextCluster == null) continue;
            if (redundancyOf(nextCluster) > redundancyOf(currentCluster)) {
                deployState.validationOverrides().invalid(ValidationId.redundancyIncrease,
                                  "Increasing redundancy from " + redundancyOf(currentCluster) + " to " +
                                  redundancyOf(nextCluster) + " in '" + currentCluster + ". " +
                                  "This is a safe operation but verify that you have room for a " +
                                  redundancyOf(nextCluster) + "/" + redundancyOf(currentCluster) + "x increase " +
                                  "in content size",
                                  deployState.now());
            }
        }
        return List.of();
    }

    private int redundancyOf(ContentCluster cluster) {
        return cluster.redundancy().finalRedundancy();
    }

}
