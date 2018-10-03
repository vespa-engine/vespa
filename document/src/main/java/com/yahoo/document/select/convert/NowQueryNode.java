// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.convert;

import com.yahoo.document.select.rule.ArithmeticNode;

/**
 * Represents the now node in a query expression.
 *
 * @author Ulf Lilleengen</a>
 */
public class NowQueryNode {
    private final long value;
    public NowQueryNode(long value) {
        this.value = value;
    }
    public NowQueryNode(ArithmeticNode node) {
    	// Assumes that the structure is checked and verified earlier
        this.value = Long.parseLong(node.getItems().get(1).getNode().toString());
    }
    @Override
    public String toString() {
        return "now(" + this.value + ")";
    }
}
