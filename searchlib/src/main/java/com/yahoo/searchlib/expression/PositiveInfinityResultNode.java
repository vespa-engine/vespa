// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * @author baldersheim
 */
public class PositiveInfinityResultNode extends ResultNode {
    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 124, PositiveInfinityResultNode.class, PositiveInfinityResultNode::new);

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    public long getInteger() {
        return Long.MAX_VALUE;
    }

    @Override
    public double getFloat() {
        return Double.MAX_VALUE;
    }

    @Override
    public byte[] getRaw() {
        return new byte[0];
    }

    @Override
    public String getString() {
        return "";
    }

    @Override
    protected int onCmp(ResultNode rhs) {
        return rhs instanceof PositiveInfinityResultNode ? 0 : 1;
    }

    @Override
    public void set(ResultNode rhs) {
    }
}
