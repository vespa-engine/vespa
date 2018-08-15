// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.search.grouping.Continuation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
class CompositeContinuation extends EncodableContinuation implements Iterable<EncodableContinuation> {

    private final List<EncodableContinuation> children = new ArrayList<>();

    public CompositeContinuation add(EncodableContinuation child) {
        children.add(child);
        return this;
    }

    @Override
    public Iterator<EncodableContinuation> iterator() {
        return children.iterator();
    }

    @Override
    public int hashCode() {
        return children.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CompositeContinuation && children.equals(((CompositeContinuation)obj).children);
    }

    @Override
    public void encode(IntegerEncoder out) {
        for (EncodableContinuation child : children) {
            child.encode(out);
        }
    }

    public static CompositeContinuation decode(IntegerDecoder from) {
        CompositeContinuation ret = new CompositeContinuation();
        while (from.hasNext()) {
            ret.add(OffsetContinuation.decode(from));
        }
        return ret;
    }
}
