// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * This class represents an open, half-open, or closed interval of strings.
 * Unbounded intervals are represented by endpoints that are null.
 *
 * @author boeker
 */
public class StringRangeItem extends TermItem {

    /** The left endpoint of this interval. Is null if the interval is unbounded to the left. */
    private final String from;

    /** Whether this interval is closed to the left, i.e., whether the left endpoint is included. */
    private final boolean fromInclusive;

    /** The right endpoint of this interval. Is null if the interval is unbounded to the right.*/
    private final String to;

    /** Whether this interval is closed to the right, i.e., whether the left right is included. */
    private final boolean toInclusive;

    /**
     * Create a StringRangeItem.
     */
    public StringRangeItem(String from, boolean fromInclusive, String to, boolean toInclusive, String indexName, boolean isFromQuery) {
        super(indexName, isFromQuery);
        this.from = from;
        this.fromInclusive = fromInclusive && from != null; // Negative infinity cannot be included
        this.to = to;
        this.toInclusive = toInclusive && to != null; // Positive infinity cannot be included
    }

    /** Returns the left endpoint of this interval, where null means negative infinity. */
    public final String getFrom() {
        return from;
    }

    /** Returns whether the left endpoint is included in the interval. */
    public final boolean isFromInclusive() {
        return fromInclusive;
    }

    /** Returns the right endpoint of this interval, where null means positive infinity. */
    public final String getTo() {
        return to;
    }

    /** Returns whether the right endpoint is included in the interval. */
    public final boolean isToInclusive() {
        return toInclusive;
    }

    /** Returns a string representation of this interval. Can be ambiguous and is only for printing purposes. */
    @Override
    public String getIndexedString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.fromInclusive ? "[" : "<" ).append(this.from != null ? "\"" + this.from + "\"" : "-Infinity");
        sb.append(";");
        sb.append(this.to != null ? "\"" + this.to+ "\"" : "Infinity").append(this.toInclusive ? "]" : ">" );
        return sb.toString();
    }

    @Override
    public String getRawWord() {
        return getIndexedString();
    }

    @Override
    public ItemType getItemType() {
        return ItemType.STRING_RANGE;
    }

    @Override
    public String getName() {
        return "STRING_RANGE";
    }

    @Override
    public String stringValue() {
        return getIndexedString();
    }

    /**
     * Not supported.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void setValue(String value) {
        throw new UnsupportedOperationException("Cannot setValue(" + value + ") on " + getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), from, fromInclusive, to, toInclusive);
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;

        StringRangeItem other = (StringRangeItem) object; // Ensured by superclass
        if (!Objects.equals(from, other.from)) return false;
        if (fromInclusive != other.fromInclusive) return false;
        if (!Objects.equals(to, other.to)) return false;
        if (toInclusive != other.toInclusive) return false;
        return true;
    }

    /**
     * Not supported.
     */
    @Override
    protected void encodeThis(ByteBuffer buffer, SerializationContext context) {
        super.encodeThis(buffer, context); // takes care of index bytes
        // Not implemented
    }

    @Override
    public int getNumWords() {
        return 1;
    }

    @Override
    public boolean isStemmed() {
        return true;
    }

    @Override
    public boolean isWords() {
        return false;
    }

    @Override
    SearchProtocol.QueryTreeItem toProtobuf(SerializationContext context) {
        var builder = SearchProtocol.ItemStringRangeTerm.newBuilder();
        builder.setProperties(ToProtobuf.buildTermProperties(this, getIndexName()));
        if (from != null) {
            builder.setLowerLimit(from);
        }
        builder.setLowerInclusive(fromInclusive);
        if (to != null) {
            builder.setUpperLimit(to);
        }
        builder.setUpperInclusive(toInclusive);
        return SearchProtocol.QueryTreeItem.newBuilder()
                .setItemStringRangeTerm(builder.build())
                .build();
    }

}
