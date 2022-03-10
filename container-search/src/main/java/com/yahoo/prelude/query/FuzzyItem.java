// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.nio.ByteBuffer;

/**
 * Fuzzy search term
 *
 * @author alexeyche
 */
public class FuzzyItem extends TermItem {
    private String fuzzyQuery;

    public FuzzyItem(String indexName, boolean isFromQuery, String fuzzyQuery) {
        super(indexName, isFromQuery, null);
        setValue(fuzzyQuery);
    }

    @Override
    public void setValue(String value) {
        this.fuzzyQuery = value;
    }

    @Override
    public String getRawWord() {
        return stringValue();
    }

    @Override
    public boolean isWords() {
        return false;
    }

    @Override
    public ItemType getItemType() {
        return ItemType.FUZZY;
    }

    @Override
    public String getName() {
        return "FUZZY";
    }

    @Override
    public String stringValue() {
        return fuzzyQuery;
    }

    @Override
    public boolean isStemmed() {
        return false;
    }

    @Override
    public String getIndexedString() {
        return stringValue();
    }

    @Override
    public int getNumWords() {
        return 1;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        FuzzyItem other = (FuzzyItem) obj;
        if (fuzzyQuery == null) {
            if (other.fuzzyQuery != null) {
                return false;
            }
        } else if (!fuzzyQuery.equals(other.fuzzyQuery)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((fuzzyQuery == null) ? 0 : fuzzyQuery.hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("FuzzyItem [fuzzyQuery=").append(fuzzyQuery).append("]");
        return builder.toString();
    }

    protected void encodeThis(ByteBuffer buffer) {
        super.encodeThis(buffer);
        putString(getIndexedString(), buffer);
    }
}

