// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class IndexKeySpanTreeTestCase {

    @Test
    public void testIndexKeys() throws Exception {
        SpanTree tree = new SpanTree("something");
        assertTrue(tree.getCurrentIndexes().isEmpty());

        tree.createIndex(SpanTree.IndexKey.SPAN_NODE);
        assertEquals(1, tree.getCurrentIndexes().size());
        assertEquals(SpanTree.IndexKey.SPAN_NODE, tree.getCurrentIndexes().iterator().next());

        tree.clearIndexes();
        assertTrue(tree.getCurrentIndexes().isEmpty());

        tree.createIndex(SpanTree.IndexKey.ANNOTATION_TYPE);
        assertEquals(1, tree.getCurrentIndexes().size());
        assertEquals(SpanTree.IndexKey.ANNOTATION_TYPE, tree.getCurrentIndexes().iterator().next());

        tree.clearIndexes();
        assertTrue(tree.getCurrentIndexes().isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSwitchIndexes()  {
        SpanTree tree = new SpanTree("something");
        assertTrue(tree.getCurrentIndexes().isEmpty());
        tree.createIndex(SpanTree.IndexKey.SPAN_NODE);
        tree.createIndex(SpanTree.IndexKey.ANNOTATION_TYPE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSwitchIndexes2()  {
        SpanTree tree = new SpanTree("something");
        assertTrue(tree.getCurrentIndexes().isEmpty());
        tree.createIndex(SpanTree.IndexKey.ANNOTATION_TYPE);
        tree.createIndex(SpanTree.IndexKey.SPAN_NODE);
    }

    @Test
    public void testSwitchIndexes3()  {
        SpanTree tree = new SpanTree("something");
        assertTrue(tree.getCurrentIndexes().isEmpty());
        tree.createIndex(SpanTree.IndexKey.ANNOTATION_TYPE);
        tree.clearIndex(SpanTree.IndexKey.SPAN_NODE);
    }
}
