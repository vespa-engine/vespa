// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

public class SearchNodeWrapper implements SearchInterface {

    private final NodeSpec nodeSpec;
    private final SearchNode node;

    public SearchNodeWrapper(NodeSpec nodeSpec, SearchNode node) {
        this.nodeSpec = nodeSpec;
        this.node = node;
    }

    @Override
    public NodeSpec getNodeSpec() {
        return nodeSpec;
    }

    @Override
    public String getHostName() {
        return node.getHostName();
    }

}
