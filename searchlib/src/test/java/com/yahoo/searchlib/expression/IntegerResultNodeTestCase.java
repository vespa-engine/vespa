// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.BufferSerializer;
import com.yahoo.vespa.objects.ObjectDumper;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
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
        assertThat(new Int8ResultNode().getClassId(), is(Int8ResultNode.classId));
        assertThat(new Int16ResultNode().getClassId(), is(Int16ResultNode.classId));
        assertThat(new Int32ResultNode().getClassId(), is(Int32ResultNode.classId));
        assertThat(new IntegerResultNode().getClassId(), is(IntegerResultNode.classId));
        assertThat(new BoolResultNode().getClassId(), is(BoolResultNode.classId));
    }

    @Test
    public void testTypeConversion() {
        for (NumericResultNode node : getResultNodes(3)) {
            assertThat(node.getInteger(), is(3l));
            assertEquals(node.getFloat(), 3.0, 0.01);
            assertThat(node.getRaw(), is(new byte[]{0, 0, 0, 0, 0, 0, 0, (byte) 3}));
            assertThat(node.getString(), is("3"));
            assertThat(node.getNumber().toString(), is("3"));
        }
    }

    @Test
    public void testMath() {
        for (NumericResultNode node : getResultNodes(5)) {
            assertThat(node.getInteger(), is(5l));
            node.negate();
            assertThat(node.getInteger(), is(-5l));
            node.multiply(new Int32ResultNode(3));
            assertThat(node.getInteger(), is(-15l));
            node.add(new Int32ResultNode(1));
            assertThat(node.getInteger(), is(-14l));
            node.divide(new Int32ResultNode(2));
            assertThat(node.getInteger(), is(-7l));
            node.modulo(new Int32ResultNode(3));
            assertThat(node.getInteger(), is(-1l));
            node.min(new Int32ResultNode(2));
            assertThat(node.getInteger(), is(-1l));
            node.min(new Int32ResultNode(-2));
            assertThat(node.getInteger(), is(-2l));
            node.max(new Int32ResultNode(-4));
            assertThat(node.getInteger(), is(-2l));
            node.max(new Int32ResultNode(4));
            assertThat(node.getInteger(), is(4l));
            assertThat(node.onCmp(new Int32ResultNode(3)), is(1));
            assertThat(node.onCmp(new Int32ResultNode(4)), is(0));
            assertThat(node.onCmp(new Int32ResultNode(5)), is(-1));
            node.set(new Int32ResultNode(8));
            assertThat(node.getInteger(), is(8l));
            assertThat(node.hashCode(), is((int)(8 + node.getClassId())));
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
        assertThat(node.getInteger(), is(5l));
    }

    @Test
    public void testInt16() {
        Int16ResultNode node = new Int16ResultNode();
        node.setValue((short)5);
        assertThat(node.getInteger(), is(5l));
    }

    @Test
    public void testInt32() {
        Int32ResultNode node = new Int32ResultNode();
        node.setValue(5);
        assertThat(node.getInteger(), is(5l));
    }

    @Test
    public void testLong() {
        IntegerResultNode node = new IntegerResultNode();
        node.setValue(5);
        assertThat(node.getInteger(), is(5l));
    }

    @Test
    public void testSerialization() throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        for (NumericResultNode node : getResultNodes(8)) {
            assertThat(node.getInteger(), is(8L));
            NumericResultNode out = node.getClass().getConstructor().newInstance();
            assertCorrectSerialization(node, out);
            assertThat(out.getInteger(), is(node.getInteger()));
        }
    }
}
