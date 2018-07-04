// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.producer.AbstractConfigProducer;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
public class SearchColumn extends AbstractConfigProducer {

    // All search nodes contained in this column, these also exist as child config producers.
    private final List<SearchNode> nodes = new LinkedList<>();

    public SearchColumn(SearchCluster parent, String name, int index) {
        super(parent, name);
    }

    /** @return The number of rows in this column. */
    public int getNumRows() { return nodes.size(); }

    /** @return All search nodes contained in this column. */
    public List<SearchNode> getSearchNodes() { return nodes; }

}
