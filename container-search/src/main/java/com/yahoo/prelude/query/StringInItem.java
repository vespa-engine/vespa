// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.compress.IntegerCompressor;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/*
 * Class representing an IN operator with a set of string values.
 *
 * @author toregge
 */
public class StringInItem extends InItem {
    private Set<String> tokens;

    public StringInItem(String indexName) {
        super(indexName);
        tokens = new HashSet<>(1000);
    }

    @Override
    public ItemType getItemType() {
        return ItemType.STRING_IN;
    }

    @Override
    public int encode(ByteBuffer buffer) {
        encodeThis(buffer);
        return 1;
    }

    @Override
    protected void encodeThis(ByteBuffer buffer) {
        super.encodeThis(buffer);
        IntegerCompressor.putCompressedPositiveNumber(tokens.size(), buffer);
        putString(getIndexName(), buffer);
        for (var entry : tokens) {
            putString(entry, buffer);
        }
    }

    @Override
    public int getTermCount() {
        return 1;
    }

    @Override
    protected void appendBodyString(StringBuilder buffer) {
        buffer.append(getIndexName());
        buffer.append("{");
        for (var entry : tokens) {
            buffer.append("\"");
            buffer.append(entry);
            buffer.append("\",");
        }
        if (!tokens.isEmpty()) {
            buffer.deleteCharAt(buffer.length() - 1); // remove extra ","
        }
        buffer.append("}");
    }

    public void addToken(String token) {
        Objects.requireNonNull(token, "Token string must not be null");
        tokens.add(token);
    }

    public void removeToken(String token) {
        tokens.remove(token);
    }

    public Collection<String> getTokens() { return Set.copyOf(tokens); }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! super.equals(o)) return false;
        var other = (StringInItem)o;
        if ( ! Objects.equals(this.tokens, other.tokens)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), tokens);
    }

}
