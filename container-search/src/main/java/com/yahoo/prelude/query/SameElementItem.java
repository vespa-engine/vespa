// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.yahoo.protect.Validator;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A query item where all terms are required to match in the same value of a multi-value field.
 *
 * @author baldersheim
 */
public class SameElementItem extends NonReducibleCompositeItem implements HasIndexItem {

    private String fieldName;

    public SameElementItem(String fieldName) {
        Validator.ensureNonEmpty("Field name", fieldName);
        this.fieldName = fieldName;
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
    public String getIndexName() {
        return fieldName;
    }

    @Override
    public int getNumWords() {
        return getItemCount();
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

    @Override
    SearchProtocol.QueryTreeItem toProtobuf() {
        var builder = SearchProtocol.ItemSameElement.newBuilder();
        var props = SearchProtocol.TermItemProperties.newBuilder();
        props.setIndex(fieldName);
        builder.setProperties(props.build());
        for (var child : items()) {
            builder.addChildren(child.toProtobuf());
        }
        return SearchProtocol.QueryTreeItem.newBuilder()
                .setItemSameElement(builder.build())
                .build();
    }

}
