// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.container.ApplicationContainer;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

/**
 * Checks that this does not remove a content cluster (or changes its id)
 * as that means losing all data of that cluster.
 *
 * @author bratseth
 */
public class ContentClusterRemovalValidator implements ChangeValidator {

    @Override
    public void validate(ChangeContext context) {
        for (String currentClusterId : context.previousModel().getContentClusters().keySet()) {
            ContentCluster nextCluster = context.model().getContentClusters().get(currentClusterId);
            if (nextCluster == null) {
                context.invalid(ValidationId.contentClusterRemoval,
                                "Content cluster '" + currentClusterId + "' is removed. " +
                                "This will cause loss of all data in this cluster");

                // If we allow the removal, we must restart all containers to ensure mbus is OK.
                for (ApplicationContainerCluster cluster : context.model().getContainerClusters().values()) {
                    context.require(new VespaRestartAction(cluster.id(),
                                                           "Content cluster '" + currentClusterId + "' has been removed",
                                                           cluster.getContainers().stream().map(ApplicationContainer::getServiceInfo).toList()));
                }
            }
        }
    }

}
