// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression;

import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.NameNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class ReferenceTestCase {

    @Test
    public void testSimple() {
        assertTrue(new Reference("foo", new Arguments(new ReferenceNode("arg")), null).isSimple());
        assertTrue(new Reference("foo", new Arguments(new ReferenceNode("arg")), "out").isSimple());
        assertTrue(new Reference("foo", new Arguments(new NameNode("arg")), "out").isSimple());
        assertFalse(new Reference("foo", new Arguments(), null).isSimple());
    }

    @Test
    public void testToString() {
        assertEquals("foo(arg_1)", new Reference("foo", new Arguments(new ReferenceNode("arg_1")), null).toString());
        assertEquals("foo(arg_1).out", new Reference("foo", new Arguments(new ReferenceNode("arg_1")), "out").toString());
        assertEquals("foo(arg_1).out", new Reference("foo", new Arguments(new NameNode("arg_1")), "out").toString());
        assertEquals("foo", new Reference("foo", new Arguments(), null).toString());
    }

    @Test
    public void testFromString() {
        Reference ref = Reference.fromIdentifier("foo_bar_1");
        assertFalse(ref.isSimple());
        assertTrue(ref.isIdentifier());
        assertEquals(0, ref.arguments().size());
        assertEquals(null, ref.output());
        assertEquals("foo_bar_1", ref.toString());

        ref = Reference.simple("foo_1", "bar_2");
        assertTrue(ref.isSimple());
        assertFalse(ref.isIdentifier());
        assertEquals(1, ref.arguments().size());
        assertTrue(ref.simpleArgument().isPresent());
        assertEquals(null, ref.output());
        assertEquals("foo_1(bar_2)", ref.toString());

        assertFalse(Reference.simple("foo").isPresent());
        assertFalse(Reference.simple("foo()").isPresent());
        assertTrue(Reference.simple("x(y)").isPresent());

        ref = Reference.simple("foo_1(bar_2)").orElseThrow();
        assertTrue(ref.isSimple());
        assertFalse(ref.isIdentifier());
        assertEquals(1, ref.arguments().size());
        assertTrue(ref.simpleArgument().isPresent());
        assertEquals("bar_2", ref.simpleArgument().get());
        assertEquals(null, ref.output());
        assertEquals("foo_1(bar_2)", ref.toString());

        ref = Reference.simple("foo_1(bar_2).baz_3").orElseThrow();
        assertTrue(ref.isSimple());
        assertFalse(ref.isIdentifier());
        assertEquals(1, ref.arguments().size());
        assertTrue(ref.simpleArgument().isPresent());
        assertEquals("baz_3", ref.output());
        assertEquals("foo_1(bar_2).baz_3", ref.toString());
    }

}
