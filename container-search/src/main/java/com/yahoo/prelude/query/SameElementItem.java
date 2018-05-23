// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;


import com.yahoo.protect.Validator;

import java.util.Iterator;

/**
 * This represents a query where all terms are required to match in the sma element id.
 * The primary usecase is to allow efficient search in arrays and maps of struct.
 * The common path is the field name containing the struct.
 * @author baldersheim
 */
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
        return buf.toString();    }

    @Override
    public int getNumWords() {
        return getItemCount();
    }

    @Override
    protected void adding(Item item) {
        Validator.ensureInstanceOf("Child item", item, TermItem.class);
        TermItem asTerm = (TermItem) item;
        Validator.ensureNotNull("Struct fieldname", asTerm.getIndexName());
        Validator.ensureNotNull("Query term", asTerm.getIndexedString());
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
