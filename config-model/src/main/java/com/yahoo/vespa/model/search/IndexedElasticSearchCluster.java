// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.producer.AbstractConfigProducer;

/**
 * @author baldersheim
 */
public class IndexedElasticSearchCluster extends IndexedSearchCluster {

    public IndexedElasticSearchCluster(AbstractConfigProducer parent, String clusterName, int index) {
        super(parent, clusterName, index);
    }

    @Override
    public int getMinNodesPerColumn() { return 0; }

    @Override
    protected void assureSdConsistent() { }

    @Override
    public int getRowBits() { return 8; }

    @Override
    boolean useFixedRowInDispatch() {
        for (SearchNode node : getSearchNodes()) {
            if (node.getNodeSpec().groupIndex() > 0) {
                return true;
            }
        }
        return false;
    }

}
