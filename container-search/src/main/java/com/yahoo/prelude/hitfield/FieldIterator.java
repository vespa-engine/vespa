// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield;

import java.util.List;
import java.util.ListIterator;

/**
 * A specialized list iterator to manipulate FieldParts in HitField objects.
 *
 * @author Steinar Knutsen
 */
public class FieldIterator implements ListIterator<FieldPart> {

    private final ListIterator<FieldPart> realIterator;
    private final HitField hitField;

    public FieldIterator(List<FieldPart> fieldList, HitField hitField) {
        this.hitField = hitField;
        realIterator = fieldList.listIterator();
    }

    public void add(FieldPart o) {
        realIterator.add(o);
        hitField.markDirty();
    }

    public boolean hasNext() {
        return realIterator.hasNext();
    }

    public boolean hasPrevious() {
        return realIterator.hasPrevious();
    }

    public FieldPart next() {
        return realIterator.next();
    }

    public int nextIndex() {
        return realIterator.nextIndex();
    }

    public FieldPart previous() {
        return realIterator.previous();
    }

    public int previousIndex() {
        return realIterator.previousIndex();
    }

    public void remove() {
        realIterator.remove();
        hitField.markDirty();
    }

    public void set(FieldPart o) {
        realIterator.set(o);
        hitField.markDirty();
    }

}
