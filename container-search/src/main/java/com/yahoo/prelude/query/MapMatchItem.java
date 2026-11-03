// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.yahoo.prelude.query.textualrepresentation.Discloser;
import com.yahoo.protect.Validator;

import java.nio.ByteBuffer;
import java.util.ListIterator;
import java.util.Objects;

/**
 * This class represents matching both key and value in a map.
 * It always has exactly two children, the key item and the value item. These may be replaced,
 * but not added or removed. It is sent to the backend as the equivalent sameElement.
 *
 * @author arnej
 */
public class MapMatchItem extends NonReducibleCompositeItem implements HasIndexItem {

    private String fieldName;
    private boolean frozen = false;

    public MapMatchItem(String fieldName, Item keyItem, Item valueItem) {
        Validator.ensureNonEmpty("Field name", fieldName);
        Objects.requireNonNull(keyItem, "The key of a map match");
        Objects.requireNonNull(valueItem, "The value of a map match");
        this.fieldName = fieldName;
        super.addItem(keyItem);
        super.addItem(valueItem);
        frozen = true;
    }

    /** Returns the item matching the key. */
    public Item keyItem() { return getItem(0); }

    /** Returns the item matching the value. */
    public Item valueItem() { return getItem(1); }

    @Override
    public String getFieldName() { return fieldName; }

    @Override
    public String getIndexName() { return fieldName; }

    /** Sets the name of the map field, and not that of the children. */
    @Override
    public void setIndexName(String index) { fieldName = index; }

    @Override
    public int getNumWords() { return getItemCount(); }

    @Override
    public ItemType getItemType() { return ItemType.SAME_ELEMENT; }

    @Override
    public String getName() { return getItemType().toString(); }

    @Override
    public void addItem(Item item) {
        throw new UnsupportedOperationException("cannot add extra children to MapMatchItem");
    }

    @Override
    public void addItem(int index, Item item) {
        throw new UnsupportedOperationException("cannot add extra children to MapMatchItem");
    }

    @Override
    public Item removeItem(int index) {
        throw new UnsupportedOperationException("cannot remove children of MapMatchItem");
    }

    @Override
    public boolean removeItem(Item item) {
        throw new UnsupportedOperationException("cannot remove children of MapMatchItem");
    }

    /** Returns an iterator over the children, which can replace them but not add or remove any. */
    @Override
    public ListIterator<Item> getItemIterator() {
        ListIterator<Item> iterator = super.getItemIterator();
        if ( ! frozen) return iterator;
        return new ListIterator<>() {
            @Override public boolean hasNext() { return iterator.hasNext(); }
            @Override public Item next() { return iterator.next(); }
            @Override public boolean hasPrevious() { return iterator.hasPrevious(); }
            @Override public Item previous() { return iterator.previous(); }
            @Override public int nextIndex() { return iterator.nextIndex(); }
            @Override public int previousIndex() { return iterator.previousIndex(); }
            @Override public void set(Item item) { iterator.set(item); }
            @Override public void remove() { throw new UnsupportedOperationException("cannot remove children of MapMatchItem"); }
            @Override public void add(Item item) { throw new UnsupportedOperationException("cannot add extra children to MapMatchItem"); }
        };
    }

    @Override
    protected void encodeThis(ByteBuffer buffer, SerializationContext context) {
        super.encodeThis(buffer, context);
        putString(fieldName, buffer);
    }

    @Override
    SearchProtocol.QueryTreeItem toProtobuf(SerializationContext context) {
        var builder = SearchProtocol.ItemSameElement.newBuilder();
        var props = SearchProtocol.TermItemProperties.newBuilder();
        props.setIndex(fieldName);
        builder.setProperties(props.build());
        for (var child : items()) {
            builder.addChildren(child.toProtobuf(context));
        }
        return SearchProtocol.QueryTreeItem.newBuilder()
                .setItemSameElement(builder.build())
                .build();
    }

    @Override
    protected void appendHeadingString(StringBuilder buffer) { }

    @Override
    protected void appendBodyString(StringBuilder buffer) {
        buffer.append(fieldName);
        buffer.append(':');
        buffer.append('{');
        super.appendBodyString(buffer);
        buffer.append('}');
    }

    @Override
    public MapMatchItem clone() {
        return (MapMatchItem) super.clone();
    }

    @Override
    public boolean equals(Object other) {
        if ( ! super.equals(other)) return false;
        return Objects.equals(this.fieldName, ((MapMatchItem)other).fieldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), fieldName);
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        discloser.addProperty("fieldName", fieldName);
    }

}
