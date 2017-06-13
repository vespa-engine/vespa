// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.10
 */
public class DummySpanNodeTestCase {

    @Test
    public void basic() {
        DummySpanNode node = DummySpanNode.INSTANCE;
        assertThat(node.getFrom(), is(0));
        assertThat(node.getTo(), is(0));
        assertThat(node.getLength(), is(0));
        assertThat(node.getText("baba"), nullValue());
        assertThat(node.isLeafNode(), is(true));
        assertThat(node.childIterator().hasNext(), is(false));
        assertThat(node.childIterator().hasPrevious(), is(false));
        assertThat(node.childIteratorRecursive().hasNext(), is(false));
        assertThat(node.childIteratorRecursive().hasPrevious(), is(false));
    }
}
