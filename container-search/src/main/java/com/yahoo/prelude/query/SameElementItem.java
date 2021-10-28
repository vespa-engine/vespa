// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.protect.Validator;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

/**
 * This represents a query where all terms are required to match in the same element id.
 * The primary usecase is to allow efficient search in arrays and maps of struct.
 * The common path is the field name containing the struct.
 *
 * @author baldersheim
 */
public class SameElementItem extends NonReducibleCompositeItem {

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
            buffer.append(term.getIndexName()).append(':').append(term.getIndexedString());
            if (i.hasNext()) {
                buffer.append(' ');
            }
        }
        buffer.append('}');
    }

    @Override
    protected void adding(Item item) {
        super.adding(item);
        //TODO See if we can require only SimpleIndexedItem instead of TermItem
        Validator.ensureInstanceOf("Child item", item, TermItem.class);
        Validator.ensureNotInstanceOf("Child item", item, WordAlternativesItem.class);
        TermItem asTerm = (TermItem) item;
        Validator.ensureNonEmpty("Struct fieldname", asTerm.getIndexName());
        Validator.ensureNonEmpty("Query term", asTerm.getIndexedString());
    }

    @Override
    public Optional<Item> extractSingleChild() {
        if (getItemCount() == 1) {
            SimpleIndexedItem child = (SimpleIndexedItem)getItem(0);
            child.setIndexName(getFieldName() + "." + child.getIndexName());
            return Optional.of(child);
        }
        return Optional.empty();
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

    @Override
    public boolean equals(Object other) {
        if ( ! super.equals(other)) return false;
        return Objects.equals(this.fieldName, ((SameElementItem)other).fieldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), fieldName);
    }

}
