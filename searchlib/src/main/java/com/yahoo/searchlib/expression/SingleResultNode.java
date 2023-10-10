// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * @author baldersheim
 */
public abstract class SingleResultNode extends ResultNode {
    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 121, NumericResultNode.class);

    /**
     * In-place addition of this result with another.
     *
     * @param rhs The result to add to this.
     */
    public abstract void add(ResultNode rhs);

    /**
     * Swaps the numerical value of this node with the smaller of this and the other.
     *
     * @param rhs The other result to evaluate.
     */
    public abstract void min(ResultNode rhs);

    /**
     * Swaps the numerical value of this node with the larger of this and the other.
     *
     * @param rhs The other result to evaluate.
     */
    public abstract void max(ResultNode rhs);

    /**
     * Return a java native, either String, Double or Long, depending on the underlying container.
     *
     * @return The underlying numeric value.
     */
    public abstract Object getValue();
}
