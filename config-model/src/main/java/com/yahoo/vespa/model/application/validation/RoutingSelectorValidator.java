// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.document.select.DocumentSelector;
import com.yahoo.vespa.model.application.validation.Validation.Context;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import com.yahoo.vespa.model.search.SearchCluster;


/**
 * Validates routing selector for search and content clusters
 */
public class RoutingSelectorValidator implements Validator {

    @Override
    public void validate(Context context) {
        for (SearchCluster cluster : context.model().getSearchClusters()) {
            if (cluster instanceof IndexedSearchCluster) {
                IndexedSearchCluster sc = (IndexedSearchCluster) cluster;
                String routingSelector = sc.getRoutingSelector();
                if (routingSelector == null) continue;
                try {
                    new DocumentSelector(routingSelector);
                } catch (com.yahoo.document.select.parser.ParseException e) {
                    context.illegal("Failed to parse routing selector for search cluster '" + sc.getClusterName() + "'", e);
                }
            }
        }
    }

}
