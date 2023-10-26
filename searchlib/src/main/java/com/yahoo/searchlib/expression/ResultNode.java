// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Identifiable;

/**
 * This abstract expression node represents the result value of execution.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public abstract class ResultNode extends Identifiable implements Comparable<ResultNode> {

    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 41, ResultNode.class);

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    public final int compareTo(ResultNode b) {
        return onCmp(b);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ResultNode && compareTo((ResultNode)obj) == 0;
    }

    /**
     * This method must be implemented by all subclasses of this to allow new results to be calculated.
     *
     * @param rhs The node to get the result from.
     */
    protected abstract void set(ResultNode rhs);

    /**
     * This method must be implemented by all subclasses of this to allow ordering of results. This method is used by
     * the {@link Cloneable} implementation.
     *
     * @param rhs The other node to compare with.
     * @return Comparable result.
     */
    protected abstract int onCmp(ResultNode rhs);

    /**
     * Returns the integer representation of this result.
     *
     * @return The value of this.
     */
    public abstract long getInteger();

    /**
     * Returns the float representation of this result.
     *
     * @return The value of this.
     */
    public abstract double getFloat();

    /**
     * Returns the string representation of this result.
     *
     * @return The value of this.
     */
    public abstract String getString();

    /**
     * Returns the raw byte array representation of this result.
     *
     * @return The value of this.
     */
    public abstract byte[] getRaw();

    /**
     * Negate the value contained within the result node.
     */
    public void negate() {
        throw new RuntimeException("Class " + getClass().getName() + " does not implement negate");
    }
}
