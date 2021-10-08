// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.ObjectDumper;
import org.junit.Test;

import java.util.regex.Pattern;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class NullResultNodeTestCase {
    @Test
    public void testNullResultNode() {
        NullResultNode nullRes = new NullResultNode();
        assertThat(nullRes.onGetClassId(), is(NullResultNode.classId));
        assertThat(nullRes.getInteger(), is(0l));
        assertThat(nullRes.getString(), is(""));
        assertThat(nullRes.getRaw(), is(new byte[0]));
        assertEquals(nullRes.getFloat(), 0.0, 0.01);
        assertThat(nullRes.onCmp(new NullResultNode()), is(0));
        assertThat(nullRes.onCmp(new IntegerResultNode(0)), is(not(0)));
        ObjectDumper dumper = new ObjectDumper();
        nullRes.visitMembers(dumper);
        assertTrue(dumper.toString().contains("result: <NULL>"));
        nullRes.set(new IntegerResultNode(3));
        assertThat(nullRes.onCmp(new IntegerResultNode(3)), is(not(0)));
    }
}
