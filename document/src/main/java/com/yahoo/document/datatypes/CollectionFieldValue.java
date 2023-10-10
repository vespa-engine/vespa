// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.document.CollectionDataType;

import java.util.Collection;
import java.util.Iterator;

/**
 * Superclass of multivalue field values
 *
 * @author Håkon Humberset
 */
public abstract class CollectionFieldValue<T extends FieldValue> extends CompositeFieldValue {

    CollectionFieldValue(CollectionDataType type) {
        super(type);
    }

    @Override
    public CollectionDataType getDataType() {
        return (CollectionDataType) super.getDataType();
    }

    /**
     * Utility function to wrap primitives.
     *
     * @see Array.ListWrapper
     */
    protected FieldValue createFieldValue(Object o) {
        if (o instanceof FieldValue) {
            if (!getDataType().getNestedType().isValueCompatible((FieldValue) o)) {
                throw new IllegalArgumentException(
                        "Incompatible data types. Got "
                                + ((FieldValue)o).getDataType() + ", expected "
                                + getDataType().getNestedType());
            }
            return (FieldValue) o;
        } else {
            FieldValue fval = getDataType().getNestedType().createFieldValue();
            fval.assign(o);
            return fval;
        }
    }

    public void verifyElementCompatibility(T o) {
        if (!getDataType().getNestedType().isValueCompatible(o)) {
            throw new IllegalArgumentException(
                    "Incompatible data types. Got "
                    + o.getDataType() + ", expected "
                    + getDataType().getNestedType());
        }
    }

    public abstract Iterator<T> fieldValueIterator();

    // Collection implementation

    public abstract boolean add(T value);

    public abstract boolean contains(Object o);

    public abstract boolean isEmpty();

    protected boolean isEmpty(Collection collection) {
        return collection.isEmpty();
    }

    public abstract Iterator<T> iterator();

    public abstract boolean removeValue(FieldValue o);

    protected boolean removeValue(FieldValue o, Collection collection) {
        int removedCount = 0;
        while (collection.remove(o)) {
            ++removedCount;
        }
        return (removedCount > 0);
    }

    public abstract int size();

}
