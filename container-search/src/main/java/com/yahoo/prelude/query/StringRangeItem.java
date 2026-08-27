// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A lexical range over the values of a string field: All values which sort between the lower and the upper
 * bound of this, using the collation of the searched field, are a match.
 * <p>
 * Either bound may be unbounded (null), and each bound is independently inclusive or exclusive, so this can
 * express e.g. <code>["a";"b"&gt;</code> (from "a" inclusive to "b" exclusive) and <code>[;"b"]</code>
 * (everything up to and including "b").
 * <p>
 * Note that a range whose lower bound sorts after its upper bound matches nothing; the bounds are not
 * reordered, in contrast to the numerical {@link RangeItem}.
 *
 * @author boeker
 */
public class StringRangeItem extends TermItem {

    /** The lower bound of this range, or null if it is unbounded */
    private final String from;

    /** Whether the lower bound is included in this range */
    private final boolean fromInclusive;

    /** The upper bound of this range, or null if it is unbounded */
    private final String to;

    /** Whether the upper bound is included in this range */
    private final boolean toInclusive;

    /**
     * Creates a string range with both bounds included.
     *
     * @param from the lower bound, or null if unbounded
     * @param to the upper bound, or null if unbounded
     * @param indexName the field to search
     */
    public StringRangeItem(String from, String to, String indexName) {
        this(from, true, to, true, indexName);
    }

    /**
     * Creates a string range.
     *
     * @param from the lower bound, or null if unbounded
     * @param fromInclusive whether the lower bound is a match
     * @param to the upper bound, or null if unbounded
     * @param toInclusive whether the upper bound is a match
     * @param indexName the field to search
     */
    public StringRangeItem(String from, boolean fromInclusive, String to, boolean toInclusive, String indexName) {
        super(indexName, false);
        this.from = from;
        this.fromInclusive = fromInclusive;
        this.to = to;
        this.toInclusive = toInclusive;
        setNormalizable(false);
    }

    /** Returns the lower bound of this range, or null if it is unbounded */
    public String getFrom() { return from; }

    /** Returns whether the lower bound of this range is a match */
    public boolean isFromInclusive() { return fromInclusive; }

    /** Returns the upper bound of this range, or null if it is unbounded */
    public String getTo() { return to; }

    /** Returns whether the upper bound of this range is a match */
    public boolean isToInclusive() { return toInclusive; }

    @Override
    public ItemType getItemType() { return ItemType.STRING_RANGE; }

    @Override
    public String getName() { return "STRING_RANGE"; }

    @Override
    public int getNumWords() { return 1; }

    /**
     * Returns the expression of this range, e.g <code>["a";"b"&gt;</code>, where an unbounded end is written as
     * nothing and a bound is written in quotes, with backslashes and quotes in it backslash escaped, such that
     * the bounds can always be told apart from the syntax of the expression.
     */
    @Override
    public String getIndexedString() {
        StringBuilder b = new StringBuilder();
        b.append(fromInclusive ? '[' : '<');
        appendBound(from, b);
        b.append(';');
        appendBound(to, b);
        b.append(toInclusive ? ']' : '>');
        return b.toString();
    }

    private static void appendBound(String bound, StringBuilder b) {
        if (bound == null) return; // Unbounded: Written as nothing, which no quoted bound can be confused with
        b.append('"');
        for (int i = 0; i < bound.length(); i++) {
            char c = bound.charAt(i);
            if (c == '\\' || c == '"')
                b.append('\\');
            b.append(c);
        }
        b.append('"');
    }

    /** Same as {@link #getIndexedString} */
    @Override
    public String stringValue() { return getIndexedString(); }

    /** Same as {@link #getIndexedString}: A range has no raw form separate from its expression */
    @Override
    public String getRawWord() { return getIndexedString(); }

    /**
     * The bounds of a range cannot be set from a single string.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void setValue(String value) {
        throw new UnsupportedOperationException("Cannot setValue(" + value + ") on " + getName());
    }

    /** A range is matched as-is, so no stemming applies to it */
    @Override
    public boolean isStemmed() { return true; }

    /** A range is not text, so no linguistic processing applies to it */
    @Override
    public boolean isWords() { return false; }

    /** String ranges use an empty heading instead of "STRING_RANGE ", like numerical ranges do */
    @Override
    protected void appendHeadingString(StringBuilder buffer) {}

    /**
     * String ranges are not part of the legacy query stack dump format, which the backend only accepts from
     * clients which do not support them: The range expression is written as the term, as for numerical ranges,
     * but the backend will not interpret it as a range.
     */
    @Override
    protected void encodeThis(ByteBuffer buffer, SerializationContext context) {
        super.encodeThis(buffer, context); // takes care of index bytes
        putString(getIndexedString(), buffer);
    }

    @Override
    SearchProtocol.QueryTreeItem toProtobuf(SerializationContext context) {
        var builder = SearchProtocol.ItemStringRangeTerm.newBuilder();
        builder.setProperties(ToProtobuf.buildTermProperties(this, getIndexName()));
        if (from != null)
            builder.setLowerLimit(from);
        if (to != null)
            builder.setUpperLimit(to);
        builder.setLowerInclusive(fromInclusive);
        builder.setUpperInclusive(toInclusive);
        return SearchProtocol.QueryTreeItem.newBuilder()
                                           .setItemStringRangeTerm(builder.build())
                                           .build();
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        discloser.addProperty("from", from);
        discloser.addProperty("fromInclusive", fromInclusive);
        discloser.addProperty("to", to);
        discloser.addProperty("toInclusive", toInclusive);
    }

    @Override
    public boolean equals(Object object) {
        if ( ! super.equals(object)) return false;

        StringRangeItem other = (StringRangeItem) object; // Ensured by superclass
        if ( ! Objects.equals(this.from, other.from)) return false;
        if ( ! Objects.equals(this.to, other.to)) return false;
        if (this.fromInclusive != other.fromInclusive) return false;
        if (this.toInclusive != other.toInclusive) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), from, fromInclusive, to, toInclusive);
    }

}
