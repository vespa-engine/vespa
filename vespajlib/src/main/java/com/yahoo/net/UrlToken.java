// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.net;

/**
 * @author Simon Thoresen Hult
 */
public class UrlToken {

    public enum Type {
        SCHEME,
        USERINFO,
        PASSWORD,
        HOST,
        PORT,
        PATH,
        QUERY,
        FRAGMENT
    }

    private final Type type;
    private final int offset;
    private final String orig;
    private final String term;

    public UrlToken(Type type, int offset, String orig, String term) {
        if (type == null) {
            throw new NullPointerException();
        }
        this.type = type;
        this.offset = offset;
        this.orig = orig;
        this.term = term;
    }

    public Type getType() {
        return type;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return orig != null ? orig.length() : 0;
    }

    public String getOrig() {
        return orig;
    }

    public String getTerm() {
        return term;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof UrlToken)) {
            return false;
        }
        UrlToken rhs = (UrlToken)obj;
        if (offset != rhs.offset) {
            return false;
        }
        if (orig != null ? !orig.equals(rhs.orig) : rhs.orig != null) {
            return false;
        }
        if (term != null ? !term.equals(rhs.term) : rhs.term != null) {
            return false;
        }
        if (type != rhs.type) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + offset;
        result = 31 * result + (orig != null ? orig.hashCode() : 0);
        result = 31 * result + (term != null ? term.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("UrlToken(");
        ret.append("type=").append(type).append(", ");
        ret.append("offset=").append(offset).append(", ");
        if (orig != null) {
            ret.append("orig='").append(orig).append("', ");
        }
        if (term != null) {
            ret.append("term='").append(term).append("', ");
        }
        ret.setLength(ret.length() - 2);
        ret.append(")");
        return ret.toString();
    }
}
