// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.yahoo.search.Result;


/**
 * An iterator for the list of hits in a result. This iterator supports the remove operation.
 *
 * @author Steinar Knutsen
 */
public class HitIterator implements Iterator<Hit> {

    /** The index into the list of hits */
    private int index = -1;

    /** The list of hits to iterate over */
    private final List<Hit> hits;

    /** The result the hits belong to */
    private final HitGroup hitGroup;

    /** Whether the iterator is in a state where remove is OK */
    private boolean canRemove = false;

    public HitIterator(HitGroup hitGroup, List<Hit> hits) {
        this.hitGroup = hitGroup;
        this.hits = hits;
    }

    public HitIterator(Result result, List<Hit> hits) {
        this.hitGroup = result.hits();
        this.hits = hits;
    }

    public boolean hasNext() {
        if (hits.size() > (index + 1)) {
            return true;
        } else {
            return false;
        }
    }

    public Hit next() throws NoSuchElementException {
        if (hits.size() <= (index + 1)) {
            throw new NoSuchElementException();
        } else {
            canRemove = true;
            return hits.get(++index);
        }
    }

    public void remove() throws IllegalStateException {
        if (!canRemove) {
            throw new IllegalStateException();
        }
        hitGroup.remove(index);
        index--;
        canRemove = false;
    }

}
