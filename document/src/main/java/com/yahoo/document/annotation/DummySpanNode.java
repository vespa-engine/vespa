// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import java.util.Collections;
import java.util.ListIterator;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
class DummySpanNode extends SpanNode {
    final static DummySpanNode INSTANCE = new DummySpanNode();

    private DummySpanNode() {
    }

    @Override
    public boolean isLeafNode() {
        return true;
    }

    @Override
    public ListIterator<SpanNode> childIterator() {
        return Collections.<SpanNode>emptyList().listIterator();
    }

    @Override
    public ListIterator<SpanNode> childIteratorRecursive() {
        return Collections.<SpanNode>emptyList().listIterator();
    }

    @Override
    public int getFrom() {
        return 0;
    }

    @Override
    public int getTo() {
        return 0;
    }

    @Override
    public int getLength() {
        return 0;
    }

    @Override
    public CharSequence getText(CharSequence text) {
        return null;
    }
}
