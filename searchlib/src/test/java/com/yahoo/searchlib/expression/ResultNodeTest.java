// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.BufferSerializer;
import com.yahoo.vespa.objects.ObjectDumper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class ResultNodeTest {
    public String dumpNode(ResultNode node) {
        ObjectDumper dump = new ObjectDumper();
        node.visitMembers(dump);
        return dump.toString();
    }

    public void assertCorrectSerialization(ResultNode from, ResultNode to) {
        BufferSerializer buffer = new BufferSerializer();
        from.serialize(buffer);
        buffer.flip();
        to.deserialize(buffer);
        assertEquals(0, from.onCmp(to));
    }

    public void assertOrder(ResultNode a, ResultNode b, ResultNode c) {
        assertTrue(a.onCmp(a) == 0);
        assertTrue(a.onCmp(b) < 0);
        assertTrue(a.onCmp(c) < 0);

        assertTrue(b.onCmp(a) > 0);
        assertTrue(b.onCmp(b) == 0);
        assertTrue(b.onCmp(c) < 0);

        assertTrue(c.onCmp(a) > 0);
        assertTrue(c.onCmp(b) > 0);
        assertTrue(c.onCmp(c) == 0);
    }
}
