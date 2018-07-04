// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.treenet.rule;

/**
 * @author Simon Thoresen Hult
 */
public class Response extends TreeNode {

    // The id of the next tree to run after this.
    private final Double value;

    // The value of this response.
    private final String next;

    /**
     * Constructs a new response.
     *
     * @param next  The id of the next tree to run after this.
     * @param value The value of this response.
     */
    public Response(Double value, String next) {
        super();
        this.value = value;
        this.next = next;
    }

    /**
     * Returns the value of this response.
     */
    public Double getValue() {
        return value;
    }

    /**
     * Returns the id of the next tree to run after this.
     */
    public String getNext() {
        return next;
    }

    @Override
    public String toRankingExpression() {
        return value.toString();
    }
}
