// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.ObjectDumper;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class NullResultNodeTestCase {
    @Test
    public void testNullResultNode() {
        NullResultNode nullRes = new NullResultNode();
        assertEquals(NullResultNode.classId, nullRes.onGetClassId());
        assertEquals(0, nullRes.getInteger());
        assertTrue(nullRes.getString().isEmpty());
        assertEquals(0, nullRes.getRaw().length);
        assertEquals(0.0, nullRes.getFloat(), 0.01);
        assertEquals(0, nullRes.onCmp(new NullResultNode()));
        assertNotEquals(0, nullRes.onCmp(new IntegerResultNode(0)));
        ObjectDumper dumper = new ObjectDumper();
        nullRes.visitMembers(dumper);
        assertTrue(dumper.toString().contains("result: <NULL>"));
        nullRes.set(new IntegerResultNode(3));
        assertNotEquals(0, nullRes.onCmp(new IntegerResultNode(3)));
    }
}
