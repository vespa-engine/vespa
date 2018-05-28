// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;


import com.google.common.annotations.Beta;
import com.yahoo.protect.Validator;

import java.util.Iterator;

/**
 * This represents a query where all terms are required to match in the same element id.
 * The primary usecase is to allow efficient search in arrays and maps of struct.
 * The common path is the field name containing the struct.
 * @author baldersheim
 */
@Beta
public class SameElementItem extends CompositeIndexedItem {

    public SameElementItem(String commonPath) {
        setIndexName(commonPath);
    }

    @Override
    public String getIndexedString() {
        StringBuilder buf = new StringBuilder();

        for (Iterator<Item> i = getItemIterator(); i.hasNext();) {
            IndexedItem indexedItem = (IndexedItem) i.next();

            buf.append(indexedItem.getIndexedString());
            if (i.hasNext()) {
                buf.append(' ');
            }
        }
        return buf.toString();
    }

    protected void appendHeadingString(StringBuilder buffer) { }
    protected void appendBodyString(StringBuilder buffer) {
        appendIndexString(buffer);
        buffer.append('{');
        for (Iterator<Item> i = getItemIterator(); i.hasNext();) {
            TermItem term = (TermItem) i.next();
            buffer.append(term.getIndexName()).append(':').append(term.getIndexedString());
            if (i.hasNext()) {
                buffer.append(' ');
            }
        }
        buffer.append('}');
    }

    @Override
    public int getNumWords() {
        return getItemCount();
    }

    @Override
    protected void adding(Item item) {
        Validator.ensureInstanceOf("Child item", item, TermItem.class);
        TermItem asTerm = (TermItem) item;
        Validator.ensureNonEmpty("Struct fieldname", asTerm.getIndexName());
        Validator.ensureNonEmpty("Query term", asTerm.getIndexedString());
    }
    @Override
    public ItemType getItemType() {
        return ItemType.SAME_ELEMENT;
    }

    @Override
    public String getName() {
        return getItemType().toString();
    }
}
