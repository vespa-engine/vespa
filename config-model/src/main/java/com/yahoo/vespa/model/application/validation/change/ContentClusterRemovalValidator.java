// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import java.util.List;

/**
 * Checks that this does not remove a content cluster (or changes its id)
 * as that means losing all data of that cluster.
 *
 * @author bratseth
 */
public class ContentClusterRemovalValidator implements ChangeValidator {

    @Override
    public List<ConfigChangeAction> validate(VespaModel current, VespaModel next, DeployState deployState) {
        for (String currentClusterId : current.getContentClusters().keySet()) {
            ContentCluster nextCluster = next.getContentClusters().get(currentClusterId);
            if (nextCluster == null)
                deployState.validationOverrides().invalid(ValidationId.contentClusterRemoval,
                                  "Content cluster '" + currentClusterId + "' is removed. " +
                                  "This will cause loss of all data in this cluster",
                                  deployState.now());
        }

        return List.of();
    }

}
