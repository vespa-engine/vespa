// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.document.select.DocumentSelector;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.search.SearchCluster;
import com.yahoo.vespa.model.search.IndexedSearchCluster;


/**
 * Validates routing selector for search and content clusters
 */
public class RoutingSelectorValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        for (SearchCluster cluster : model.getSearchClusters()) {
            if (cluster instanceof IndexedSearchCluster) {
                IndexedSearchCluster sc = (IndexedSearchCluster) cluster;
                String routingSelector = sc.getRoutingSelector();
                if (routingSelector == null) continue;
                try {
                    new DocumentSelector(routingSelector);
                } catch (com.yahoo.document.select.parser.ParseException e) {
                    throw new IllegalArgumentException("Failed to parse routing selector for search cluster '" +
                                                       sc.getClusterName() + "'", e);
                }
            }
        }
    }

}
