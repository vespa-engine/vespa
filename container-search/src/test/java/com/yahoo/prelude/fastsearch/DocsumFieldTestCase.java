// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.assertEquals;

/**
 * Tests DocsumField class functionality
 *
 * @author Bjørn Borud
 */
public class DocsumFieldTestCase {

    @Test
    public void testConstructors() {
        DocsumField.create("test", "string");
        DocsumField.create("test", "integer");
        DocsumField.create("test", "byte");
        DocsumField.create("test", "int64");
    }

    @Test
    public void testByte() {
        FastHit hit = new FastHit();
        DocsumField c = DocsumField.create("test", "byte");
        byte[] byteData = { 10, 20, 30, 40};
        ByteBuffer buffer = ByteBuffer.wrap(byteData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        c.decode(buffer, hit);
        assertEquals(1, buffer.position());
        assertEquals("10", hit.getField("test").toString());

        c.decode(buffer, hit);
        assertEquals(2, buffer.position());
        assertEquals("20", hit.getField("test").toString());

        c.decode(buffer, hit);
        assertEquals(3, buffer.position());
        assertEquals("30", hit.getField("test").toString());
    }

    @Test
    public void testLongString() {
        FastHit hit = new FastHit();
        DocsumField c = DocsumField.create("test", "longstring");
        byte[] byteData = { 4, 0, 0, 0, 'c', 'a', 'f', 'e', 4, 0, 0, 0, 'B', 'A', 'B', 'E' };
        ByteBuffer buffer = ByteBuffer.wrap(byteData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        c.decode(buffer, hit);
        assertEquals(8, buffer.position());
        assertEquals("cafe", hit.getField("test"));

        c.decode(buffer, hit);
        assertEquals(16, buffer.position());
        assertEquals("BABE", hit.getField("test"));
    }

}
