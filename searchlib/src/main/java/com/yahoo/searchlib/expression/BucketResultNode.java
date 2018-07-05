// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

/**
 * This is the superclass of all bucket values
 *
 * @author <a href="mailto:havardpe@yahoo-inc.com">Haavard Pettersen</a>
 * @author baldersheim
 * @author Simon Thoresen Hult
 */
abstract public class BucketResultNode extends ResultNode {

    // The global class identifier shared with C++.
    public static final int classId = registerClass(0x4000 + 100, BucketResultNode.class);

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

    /**
     * Tell if this bucket has zero width. Indicates that is has no value and can be considered a NULL range. An empty
     * range is used by the backend to represent hits that end in no buckets.
     *
     * @return If this bucket has zero width.
     */
    public abstract boolean empty();
}
