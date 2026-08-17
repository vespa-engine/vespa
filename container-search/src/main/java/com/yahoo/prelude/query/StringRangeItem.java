// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * This class represents an open, half-open, or closed interval of strings.
 *
 * @author boeker
 */
public class StringRangeItem extends TermItem {

    /** The left endpoint of this interval. Is null if the interval is unbounded to the left. */
    private String left;

    /** Whether this interval is closed to the left, i.e., whether the left endpoint is included. */
    private boolean left_closed;

    /** The right endpoint of this interval. Is null if the interval is unbounded to the right.*/
    private String right;

    /** Whether this interval is closed to the right, i.e., whether the left right is included. */
    private boolean right_closed;

    private String expression;

    private int hitLimit = 0;

    /**
     * Creates an int item which must be equal to the given int number -
     * that is both the lower and upper limit is this number
     */
    public StringRangeItem(String left, boolean left_closed, String right, boolean right_closed, String indexName, boolean isFromQuery) {
        super(indexName, isFromQuery);
        this.left = left;
        this.left_closed = left_closed && left != null;
        this.right = right;
        this.right_closed = right_closed && right != null;

        StringBuilder sb = new StringBuilder();
        sb.append(this.left_closed ? "[" : "<" ).append(this.left != null ? this.left : "");
        sb.append(";");
        sb.append(this.right != null ? this.right : "").append(this.right_closed ? "]" : ">" );
        expression = sb.toString();
    }

    /** Returns the left endpoint of this interval, which may be null if the endpoint is negative infinity. */
    public final String getLeftEndpoint() {
        return left;
    }

    /** Returns the right endpoint of this interval, which may be null if the endpoint is positive infinity. */
    public final String getRightEndpoint() {
        return right;
    }

    /**
     * Returns the number of hits this will match, or 0 if all should be matched.
     * If this number is positive, the hits closest to <code>left</code> are returned, and if
     * this number is negative the hits closest to <code>right</code> are returned.
     */
    public final int getHitLimit() {
        return hitLimit;
    }

    /**
     * Sets the number of hits this will match, or 0 if all should be matched.
     * If this number is positive, the hits closest to <code>left</code> are returned, and if
     * this number is negative the hits closest to <code>right</code> are returned.
     *
     * @param hitLimit number of hits to match for this operator
     */
    public final void setHitLimit(int hitLimit) {
        this.hitLimit = hitLimit;
    }

    @Override
    public String getRawWord() {
        return expression;
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
        return expression;
    }

    @Override
    public void setValue(String value) {
        // TODO
    }

    @Override
    public int hashCode() {
        // TODO Do not use expression here
        return Objects.hash(super.hashCode(), expression);
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;

        StringRangeItem other = (StringRangeItem) object; // Ensured by superclass
        // TODO Do not use expression here
        if (!expression.equals(other.expression)) return false;
        if (getHitLimit() != other.getHitLimit()) return false;
        return true;
    }

    @Override
    public String getIndexedString() {
        return expression;
    }

    @Override
    protected void encodeThis(ByteBuffer buffer, SerializationContext context) {
        super.encodeThis(buffer, context); // takes care of index bytes
        // TODO Correct?
        putString(expression, buffer);
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
        if (left != null) {
            builder.setLeft(left);
        }
        if (right != null) {
            builder.setRight(right);
        }
        builder.setLeftClosed(left_closed);
        builder.setRightClosed(right_closed);
        if (hitLimit != 0) {
            builder.setHasRangeLimit(true);
            builder.setRangeLimit(hitLimit);
        }
        return SearchProtocol.QueryTreeItem.newBuilder()
                .setItemStringRangeTerm(builder.build())
                .build();
    }

}
