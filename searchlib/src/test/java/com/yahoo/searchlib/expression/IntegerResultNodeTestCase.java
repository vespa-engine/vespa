// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class IntegerResultNodeTestCase extends ResultNodeTest {

    List<NumericResultNode> getResultNodes(long startvalue) {
        return Arrays.asList(new Int8ResultNode((byte)startvalue),
                             new Int16ResultNode((short)startvalue),
                             new Int32ResultNode((int)startvalue),
                             new IntegerResultNode(startvalue));
    }

    @Test
    public void testClassId() {
        assertEquals(Int8ResultNode.classId, new Int8ResultNode().getClassId());
        assertEquals(Int16ResultNode.classId, new Int16ResultNode().getClassId());
        assertEquals(Int32ResultNode.classId, new Int32ResultNode().getClassId());
        assertEquals(IntegerResultNode.classId, new IntegerResultNode().getClassId());
        assertEquals(BoolResultNode.classId, new BoolResultNode().getClassId());
    }

    @Test
    public void testTypeConversion() {
        for (NumericResultNode node : getResultNodes(3)) {
            assertEquals(3, node.getInteger());
            assertEquals(3.0, node.getFloat(), 0.01);
            assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 0, 0, (byte) 3}, node.getRaw());
            assertEquals("3", node.getString());
            assertEquals("3", node.getNumber().toString());
        }
    }

    @Test
    public void testMath() {
        for (NumericResultNode node : getResultNodes(5)) {
            assertEquals(5, node.getInteger());
            node.negate();
            assertEquals(-5, node.getInteger());
            node.multiply(new Int32ResultNode(3));
            assertEquals(-15, node.getInteger());
            node.add(new Int32ResultNode(1));
            assertEquals(-14, node.getInteger());
            node.divide(new Int32ResultNode(2));
            assertEquals(-7, node.getInteger());
            node.modulo(new Int32ResultNode(3));
            assertEquals(-1, node.getInteger());
            node.min(new Int32ResultNode(2));
            assertEquals(-1, node.getInteger());
            node.min(new Int32ResultNode(-2));
            assertEquals(-2, node.getInteger());
            node.max(new Int32ResultNode(-4));
            assertEquals(-2, node.getInteger());
            node.max(new Int32ResultNode(4));
            assertEquals(4, node.getInteger());
            assertEquals(1, node.onCmp(new Int32ResultNode(3)));
            assertEquals(0, node.onCmp(new Int32ResultNode(4)));
            assertEquals(-1, node.onCmp(new Int32ResultNode(5)));
            node.set(new Int32ResultNode(8));
            assertEquals(8, node.getInteger());
            assertEquals(8 + node.getClassId(), node.hashCode());
            assertTrue(dumpNode(node).contains("value: 8"));
        }
    }

    @Test
    public void testBool() {
        BoolResultNode node = new BoolResultNode();
        assertEquals(0, node.getInteger());
        assertEquals(0.0, node.getFloat(), 0.000000000001);
        assertEquals("false", node.getString());
        node.setValue(true);
        assertEquals(1, node.getInteger());
        assertEquals(1.0, node.getFloat(), 0.000000000001);
        assertEquals("true", node.getString());
    }

    @Test
    public void testInt8() {
        Int8ResultNode node = new Int8ResultNode();
        node.setValue((byte) 5);
        assertEquals(5, node.getInteger());
    }

    @Test
    public void testInt16() {
        Int16ResultNode node = new Int16ResultNode();
        node.setValue((short)5);
        assertEquals(5, node.getInteger());
    }

    @Test
    public void testInt32() {
        Int32ResultNode node = new Int32ResultNode();
        node.setValue(5);
        assertEquals(5, node.getInteger());
    }

    @Test
    public void testLong() {
        IntegerResultNode node = new IntegerResultNode();
        node.setValue(5);
        assertEquals(5, node.getInteger());
    }

    @Test
    public void testSerialization() throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        for (NumericResultNode node : getResultNodes(8)) {
            assertEquals(8, node.getInteger());
            NumericResultNode out = node.getClass().getConstructor().newInstance();
            assertCorrectSerialization(node, out);
            assertEquals(out.getInteger(), node.getInteger());
        }
    }
}
