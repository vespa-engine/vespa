// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.nio.ByteBuffer;
import java.util.regex.Pattern;

/**
 * Match a field with the contained regular expression.
 *
 * @author Steinar Knutsen
 */
public class RegExpItem extends TermItem {

    private String expression;
    private Pattern regexp;

    public RegExpItem(String indexName, boolean isFromQuery, String expression) {
        super(indexName, isFromQuery, null);
        setValue(expression);
    }

    @Override
    public String stringValue() {
        return expression;
    }

    @Override
    public boolean isStemmed() {
        return true;
    }

    @Override
    public int getNumWords() {
        return 1;
    }

    @Override
    public void setValue(String expression) {
        regexp = Pattern.compile(expression);
        this.expression = expression;
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
    public String getIndexedString() {
        return stringValue();
    }

    @Override
    public ItemType getItemType() {
        return ItemType.REGEXP;
    }

    @Override
    public String getName() {
        return ItemType.REGEXP.name();
    }

    protected void encodeThis(ByteBuffer buffer) {
        super.encodeThis(buffer);
        putString(getIndexedString(), buffer);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("RegExpItem [expression=").append(expression).append("]");
        return builder.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((expression == null) ? 0 : expression.hashCode());
        return result;
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
        RegExpItem other = (RegExpItem) obj;
        if (expression == null) {
            if (other.expression != null) {
                return false;
            }
        } else if (!expression.equals(other.expression)) {
            return false;
        }
        return true;
    }

    public Pattern getRegexp() { return regexp; }

}
