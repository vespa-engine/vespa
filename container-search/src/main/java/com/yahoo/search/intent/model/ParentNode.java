// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.intent.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A node which is not a leaf in the intent tree
 *
 * @author bratseth
 */
public abstract class ParentNode<T extends Node> extends Node {

    private List<T> children=new ArrayList<>();

    public ParentNode() {
        super(1.0);
    }

    public ParentNode(double score) {
        super(score);
    }

    /**
     * This returns the children of this node in the intent tree.
     * This is never null. Children can be added and removed from this list to modify this node.
     */
    public List<T> children() { return children; }

    @Override void addSources(double weight,Map<Source,SourceNode> sources) {
        for (T child : children)
            child.addSources(weight*getScore(),sources);
    }

}
