// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.10
 */
public class DummySpanNodeTestCase {

    @Test
    public void basic() {
        DummySpanNode node = DummySpanNode.INSTANCE;
        assertEquals(0, node.getFrom());
        assertEquals(0, node.getTo());
        assertEquals(0, node.getLength());
        assertNull(node.getText("baba"));
        assertTrue(node.isLeafNode());
        assertFalse(node.childIterator().hasNext());
        assertFalse(node.childIterator().hasPrevious());
        assertFalse(node.childIteratorRecursive().hasNext());
        assertFalse(node.childIteratorRecursive().hasPrevious());
    }
}
