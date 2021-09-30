// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.document.DocumentOperation;
import org.junit.Test;

import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Einar M R Rosenvinge
 * @since 5.1.10
 */
public class ProcessingTestCase {

    @Test
    public void serviceName() {
        assertEquals("default", new Processing().getServiceName());
        assertEquals("foobar", new Processing("foobar", (DocumentOperation) null, null).getServiceName());
    }

    @Test
    public void contextVariables() {
        Processing p = new Processing();

        p.setVariable("foo", "banana");
        p.setVariable("bar", "apple");

        Iterator<Map.Entry<String, Object>> it = p.getVariableAndNameIterator();
        assertTrue(it.hasNext());
        assertNotNull(it.next());
        assertTrue(it.hasNext());
        assertNotNull(it.next());
        assertFalse(it.hasNext());

        assertTrue(p.hasVariable("foo"));
        assertTrue(p.hasVariable("bar"));
        assertFalse(p.hasVariable("baz"));

        assertEquals("banana", p.removeVariable("foo"));

        p.clearVariables();

        it = p.getVariableAndNameIterator();
        assertFalse(it.hasNext());
    }
}
