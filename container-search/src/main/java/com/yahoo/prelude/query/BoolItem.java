// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.processing.IllegalInputException;

import java.nio.ByteBuffer;

/**
 * A true/false term suitable for searching bool indexes.
 */
public class BoolItem extends TermItem {

    private boolean value;

    public BoolItem(boolean value) {
        this(value, "");
    }

    public BoolItem(boolean value, String indexName) { this(value, indexName, false); }

    public BoolItem(boolean value, String indexName, boolean isFromQuery) {
        super(indexName, isFromQuery, new Substring(String.valueOf(value)));
        this.value = value;
    }

    /** Returns ItemType.WORD as we do not want a string binding from the parsed query to index types */
    @Override
    public ItemType getItemType() { return ItemType.WORD; }

    @Override
    public String getName() { return "BOOL"; }

    @Override
    protected void encodeThis(ByteBuffer buffer) {
        super.encodeThis(buffer); // takes care of index bytes
        putString(stringValue(), buffer);
    }

    public boolean value() { return value; }

    /** Returns "true" or "false" */
    @Override
    public String stringValue() { return value ? "true" : "false"; }

    public void setValue(boolean value) {
        this.value = value;
    }

    /**
     * Sets the value from a string
     *
     * @param stringValue "true" or "false"
     * @throws IllegalArgumentException if the given value is not equal to "true" nor "false" (ignoring case)
     */
    @Override
    public void setValue(String stringValue) {
        this.value = toBoolean(stringValue);
    }

    private boolean toBoolean(String stringValue) {
        switch (stringValue.toLowerCase()) {
            case "true" : return true;
            case "false" : return false;
            default: throw new IllegalInputException("Expected 'true' or 'false', got '" + stringValue + "'");
        }
    }

    /** Returns the same as stringValue */
    @Override
    public String getRawWord() {
        return stringValue();
    }

    @Override
    public boolean isStemmed() { return false; }

    /** Word items uses a empty heading instead of "WORD " */
    @Override
    protected void appendHeadingString(StringBuilder buffer) {}

    @Override
    public int hashCode() {
        return Boolean.hashCode(value);
    }

    @Override
    public boolean equals(Object object) {
        if ( ! super.equals(object)) return false;

        BoolItem other = (BoolItem) object; // Ensured by superclass
        return this.value == other.value;
    }

    @Override
    public int getNumWords() { return 1; }

    @Override
    public String getIndexedString() { return stringValue(); }

    /** Returns true if this consists of regular word characters. Returns false if this represents a "special token" */
    @Override
    public boolean isWords() { return false; }

}
