// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.*;

/**
 * @author baldersheim
 */
public class RawTestCase {
    @Test
    public void requireThatAssignWorks() {
        byte [] buf = {0,1,2,3};
        byte [] empty = {};
        ByteBuffer bb = ByteBuffer.wrap(buf);
        Raw a = new Raw();
        a.assign(buf);
        assertEquals(bb, a.getWrappedValue());
        a.assign(empty);
        assertNotEquals(bb, a.getWrappedValue());
        a.assign(bb);
        assertEquals(bb, a.getWrappedValue());
        a.assign(empty);
        assertNotEquals(bb, a.getWrappedValue());
        a.assign(new Raw(bb));
        assertEquals(bb, a.getWrappedValue());
    }

}
