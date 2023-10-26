// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;


import java.util.List;
import java.util.ListIterator;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class SerialIterator extends RecursiveNodeIterator {
    SerialIterator(List<ListIterator<SpanNode>> iterators) {
        //the first iterator must be on top of the stack:
        for (int i = iterators.size() - 1; i > -1; i--) {
            stack.push(new PeekableListIterator<SpanNode>(iterators.get(i)));
        }
    }

    @Override
    public boolean hasNext() {
        if (stack.isEmpty()) {
            return false;
        }
        PeekableListIterator<SpanNode> iterator = stack.peek();
        if (!iterator.hasNext()) {
            stack.pop();
            return hasNext();
        }
        return true;
    }
}
