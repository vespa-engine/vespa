// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * This result holds nothing.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public abstract class ResultNodeVector extends ResultNode {

    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 108, ResultNodeVector.class);

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    public long getInteger() {
        return 0;
    }

    @Override
    public double getFloat() {
        return 0.0;
    }

    @Override
    public String getString() {
        return "";
    }

    @Override
    public byte[] getRaw() {
        return new byte[0];
    }

    @Override
    public void set(ResultNode rhs) {
    }

    public abstract ResultNodeVector add(ResultNode r);

    public abstract int size();
}
