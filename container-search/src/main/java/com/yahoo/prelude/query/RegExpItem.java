// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.nio.ByteBuffer;
import java.util.Objects;
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

    public Pattern getRegexp() { return regexp; }

    @Override
    public String toString() {
        return "RegExpItem [expression=" + expression + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), expression);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! super.equals(o)) return false;
        if (getClass() != o.getClass()) return false;

        RegExpItem other = (RegExpItem)o;
        return Objects.equals(this.expression, other.expression);
    }

}
