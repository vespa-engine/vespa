// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.convert;

import com.yahoo.document.select.rule.ArithmeticNode;

/**
 * Represents the now node in a query expression.
 *
 * @author Ulf Lilleengen
 */
public class NowQueryNode {
    private final long value;
    NowQueryNode(long value) {
        this.value = value;
    }
    NowQueryNode(ArithmeticNode node) {
        // Assumes that the structure is checked and verified earlier
        this.value = Long.parseLong(node.getItems().get(1).getNode().toString());
    }
    @Override
    public String toString() {
        return "now(" + this.value + ")";
    }
}
