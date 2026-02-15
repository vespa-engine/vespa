// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.yahoo.protect.Validator;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A query item where all terms are required to match in the same value of a multi-value field.
 *
 * @author baldersheim
 */
public class SameElementItem extends NonReducibleCompositeItem implements HasIndexItem {

    private String fieldName;
    private List<Integer> elementFilter = new ArrayList<>();

    public SameElementItem(String fieldName) {
        Validator.ensureNonEmpty("Field name", fieldName);
        this.fieldName = fieldName;
    }

    @Override
    protected void encodeThis(ByteBuffer buffer, SerializationContext context) {
        super.encodeThis(buffer, context);
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

    /**
     * Returns the element filter. If set, only the element ids in the element filter is required to match.
     */
    public List<Integer> getElementFilter() {
        return elementFilter;
    }

    /**
     * Set an element filter. The filter can not contain null values or negative numbers.
     * <p>
     * The filter will be deduplicates and sorted.
     */
    public void setElementFilter(List<Integer> filter) {
        if (filter == null || filter.isEmpty()) {
            elementFilter = new ArrayList<>();
            return;
        }
        elementFilter = filter.stream()
                .peek(val -> {
                    if (val == null) {
                        throw new IllegalArgumentException("elementFilter cannot contain null values");
                    }
                    if (val < 0) {
                        throw new IllegalArgumentException("elementFilter values must be non-negative, got: " + val);
                    }
                })
                .distinct()
                .sorted()
                .toList();
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
    SearchProtocol.QueryTreeItem toProtobuf(SerializationContext context) {
        var builder = SearchProtocol.ItemSameElement.newBuilder();
        var props = SearchProtocol.TermItemProperties.newBuilder();
        props.setIndex(fieldName);
        builder.setProperties(props.build());
        for (var filter : elementFilter) {
            builder.addElementFilter(filter);
        }
        for (var child : items()) {
            builder.addChildren(child.toProtobuf(context));
        }
        return SearchProtocol.QueryTreeItem.newBuilder()
                .setItemSameElement(builder.build())
                .build();
    }

}
