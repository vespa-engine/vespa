// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.protect.Validator;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

/**
 * This represents a query where all terms are required to match in the same element id.
 * The primary use case is to allow efficient search in arrays and maps of struct.
 * The common path is the field name containing the struct.
 *
 * @author baldersheim
 */
public class SameElementItem extends NonReducibleCompositeItem {

    private String fieldName;

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
        super.appendBodyString(buffer);
        buffer.append('}');
    }

    @Override
    public Optional<Item> extractSingleChild() {
        if (getItemCount() == 1) {
            if (getItem(0) instanceof SimpleIndexedItem child) {
                if ( ! child.getIndexName().isEmpty())
                    child.setIndexName(getFieldName() + "." + child.getIndexName());
            }
            return Optional.of(getItem(0));
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
    public void setIndexName(String index) {
        fieldName = index;
    }

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
