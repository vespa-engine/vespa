// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.ObjectVisitor;

/**
 * This result holds nothing.
 *
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
public class NullResultNode extends ResultNode {

    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 57, NullResultNode.class, NullResultNode::new);

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
    protected int onCmp(ResultNode rhs) {
        return classId - rhs.getClassId();
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("result", null);
    }

    @Override
    public void set(ResultNode rhs) {
    }
}
