// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.google.common.annotations.Beta;
import com.yahoo.protect.Validator;

import java.nio.ByteBuffer;
import java.util.Iterator;

/**
 * This represents a query where all terms are required to match in the same element id.
 * The primary usecase is to allow efficient search in arrays and maps of struct.
 * The common path is the field name containing the struct.
 * @author baldersheim
 */
@Beta
public class SameElementItem extends CompositeItem {

    private final String fieldName;

    public SameElementItem(String commonPath) {
        Validator.ensureNonEmpty("Field name", commonPath);
        this.fieldName = commonPath;
    }

    @Override
    protected void encodeThis(ByteBuffer buffer) {
        super.encodeThis(buffer);
        putString(fieldName, buffer);
    }

    @Override
    protected void appendHeadingString(StringBuilder buffer) { }
    @Override
    protected void appendBodyString(StringBuilder buffer) {
        buffer.append(fieldName).append(':');
        buffer.append('{');
        for (Iterator<Item> i = getItemIterator(); i.hasNext();) {
            TermItem term = (TermItem) i.next();
            buffer.append(extractSubFieldName(term)).append(':').append(term.getIndexedString());
            if (i.hasNext()) {
                buffer.append(' ');
            }
        }
        buffer.append('}');
    }

    @Override
    protected void adding(Item item) {
        Validator.ensureInstanceOf("Child item", item, TermItem.class);
        TermItem asTerm = (TermItem) item;
        Validator.ensureNonEmpty("Struct fieldname", asTerm.getIndexName());
        Validator.ensureNonEmpty("Query term", asTerm.getIndexedString());
        Validator.ensure("Struct fieldname does not start with '" + getFieldName() + "'",
                !asTerm.getIndexName().startsWith(fieldName));
        item.setIndexName(fieldName + '.' + asTerm.getIndexName());
    }
    @Override
    public ItemType getItemType() {
        return ItemType.SAME_ELEMENT;
    }

    @Override
    public String getName() {
        return getItemType().toString();
    }
    public String getFieldName() { return fieldName; }
    public String extractSubFieldName(TermItem full) {
        return full.getIndexName().substring(getFieldName().length()+1);
    }
}
