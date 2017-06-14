// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class IndexKeySpanTreeTestCase {

    @Test
    public void testIndexKeys() throws Exception {
        SpanTree tree = new SpanTree("something");
        assertThat(tree.getCurrentIndexes().isEmpty(), is(true));

        tree.createIndex(SpanTree.IndexKey.SPAN_NODE);
        assertThat(tree.getCurrentIndexes().size(), is(1));
        assertThat(tree.getCurrentIndexes().iterator().next(), is(SpanTree.IndexKey.SPAN_NODE));

        tree.clearIndexes();
        assertThat(tree.getCurrentIndexes().isEmpty(), is(true));

        tree.createIndex(SpanTree.IndexKey.ANNOTATION_TYPE);
        assertThat(tree.getCurrentIndexes().size(), is(1));
        assertThat(tree.getCurrentIndexes().iterator().next(), is(SpanTree.IndexKey.ANNOTATION_TYPE));

        tree.clearIndexes();
        assertThat(tree.getCurrentIndexes().isEmpty(), is(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSwitchIndexes()  {
        SpanTree tree = new SpanTree("something");
        assertThat(tree.getCurrentIndexes().isEmpty(), is(true));
        tree.createIndex(SpanTree.IndexKey.SPAN_NODE);
        tree.createIndex(SpanTree.IndexKey.ANNOTATION_TYPE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSwitchIndexes2()  {
        SpanTree tree = new SpanTree("something");
        assertThat(tree.getCurrentIndexes().isEmpty(), is(true));
        tree.createIndex(SpanTree.IndexKey.ANNOTATION_TYPE);
        tree.createIndex(SpanTree.IndexKey.SPAN_NODE);
    }

    @Test
    public void testSwitchIndexes3()  {
        SpanTree tree = new SpanTree("something");
        assertThat(tree.getCurrentIndexes().isEmpty(), is(true));
        tree.createIndex(SpanTree.IndexKey.ANNOTATION_TYPE);
        tree.clearIndex(SpanTree.IndexKey.SPAN_NODE);
    }
}
