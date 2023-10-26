// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.gbdt;

import org.w3c.dom.Node;

import java.util.Optional;

/**
 * @author bratseth
 */
public abstract class TreeNode {

    private final Optional<Integer> samples;

    public TreeNode(Optional<Integer> samples) {
        this.samples = samples;
    }

    public abstract String toRankingExpression();

    /**
     * Returns the number of samples in the training set that matches this node
     * if this model does not contain this information (i.e if it is not an "ext" model).
     */
    public Optional<Integer> samples() { return samples; }

    public static TreeNode fromDom(Node node) {
        String nodeName = node.getNodeName();
        if (nodeName.equalsIgnoreCase("node")) {
            return FeatureNode.fromDom(node);
        } else if (nodeName.equalsIgnoreCase("response")) {
            return ResponseNode.fromDom(node);
        } else {
            throw new UnsupportedOperationException(nodeName);
        }
    }

    static Optional<Integer> toInteger(Optional<String> integerText) {
        if ( ! integerText.isPresent()) return Optional.empty();
        return Optional.of(Integer.parseInt(integerText.get()));
    }

}
