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

}
