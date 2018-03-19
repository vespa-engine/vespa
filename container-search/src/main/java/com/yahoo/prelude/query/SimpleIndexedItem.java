// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.prelude.query.textualrepresentation.Discloser;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.nio.ByteBuffer;

/**
 * Common implementation for Item classes implementing the IndexedItem interface.
 * Note that this file exist in 3 copies that should be kept in sync:
 *
 * CompositeIndexedItem.java
 * SimpleIndexedItem.java
 * IndexedSegmentItem.java
 *
 * These should only have trivial differences.
 * (multiple inheritance or mixins would have been nice).
 *
 * @author arnej27959
 */
public abstract class SimpleIndexedItem extends SimpleTaggableItem implements IndexedItem {

    @NonNull
    private String index = "";

    /** The name of the index this belongs to, or "" (never null) if not specified */
    @NonNull
    public String getIndexName() {
        return index;
    }

    // encode index bytes
    protected void encodeThis(ByteBuffer buffer) {
        super.encodeThis(buffer);
        putString(index, buffer);
    }

    /** Sets the name of the index to search */
    public void setIndexName(String index) {
        if (index == null) {
            index = "";
        }
        
        this.index = index;
    }

    /** Appends the index prefix if necessary */
    protected void appendIndexString(StringBuilder buffer) {
        if (!getIndexName().equals("")) {
            buffer.append(getIndexName());
            buffer.append(":");
        }
    }

    public boolean equals(Object object) {
        if (!super.equals(object)) {
            return false;
        }
        IndexedItem other = (IndexedItem) object; // Ensured by superclass
        if (!this.index.equals(other.getIndexName())) {
            return false;
        }
        return true;
    }

    public int hashCode() {
        return super.hashCode() + 113 * index.hashCode();
    }

    public abstract String getIndexedString();

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        discloser.addProperty("index", index);
    }

}
