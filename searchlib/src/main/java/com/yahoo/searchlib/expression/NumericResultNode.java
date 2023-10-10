// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * This is a superclass for all numerical results.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
abstract public class NumericResultNode extends SingleResultNode {

    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 50, NumericResultNode.class);

    /**
     * In-place multiplication of this result with another.
     *
     * @param rhs The result to multiply with this.
     */
    public abstract void multiply(ResultNode rhs);

    /**
     * In-place division of this result with another.
     *
     * @param rhs The result to divide this by.
     */
    public abstract void divide(ResultNode rhs);

    /**
     * In-place modulo of this result with another.
     *
     * @param rhs The result to modulo this with.
     */
    public abstract void modulo(ResultNode rhs);

    /**
     * Return a java numeric, either Double or Long, depending on the underlying container.
     *
     * @return The underlying numeric value.
     */
    public abstract Object getNumber();

    @Override
    public Object getValue() {
        return getNumber();
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }
}
