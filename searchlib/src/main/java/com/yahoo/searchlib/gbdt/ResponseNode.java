// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.gbdt;

import org.w3c.dom.Node;

import java.util.Optional;

/**
 * @author Simon Thoresen Hult
 */
public class ResponseNode extends TreeNode {

    private final double value;

    public ResponseNode(double value, Optional<Integer> samples) {
        super(samples);
        this.value = value;
    }

    public double value() {
        return value;
    }

    @Override
    public String toRankingExpression() {
        return String.valueOf(value);
    }

    public static ResponseNode fromDom(Node node) {
        return new ResponseNode(Double.valueOf(XmlHelper.getAttributeText(node, "value")),
                                toInteger(XmlHelper.getOptionalAttributeText(node, "nSamples")));
    }
}
