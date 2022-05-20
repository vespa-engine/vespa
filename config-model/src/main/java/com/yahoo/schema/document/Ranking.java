// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import java.io.Serializable;

/**
 * The rank settings given in a rank clause in the search definition.
 *
 * @author Vegard Havdal
 */
public class Ranking implements Cloneable, Serializable {

    private boolean literal = false;
    private boolean filter = false;
    private boolean normal = false;

    /**
     * <p>Returns whether literal (non-stemmed, non-normalized) forms of the words should
     * be indexed in a separate index which is searched by a automatically added rank term
     * during searches.</p>
     *
     * <p>Default is false.</p>
     */
    public boolean isLiteral() { return literal; }

    public void setLiteral(boolean literal) { this.literal = literal; }

    /**
     * <p>Returns whether this is a filter. Filters will only tell if they are matched or not,
     * no detailed relevance information will be available about the match.</p>
     *
     * <p>Matching a filter is much cheaper for the search engine than matching a regular field.</p>
     *
     * <p>Default is false.</p>
     */
    public boolean isFilter() { return filter && !normal; }

    public void setFilter(boolean filter) { this.filter = filter; }

    /** Whether user has explicitly requested normal (non-filter) behavior */
    public boolean isNormal() { return normal; }
    public void setNormal(boolean n) { this.normal = n; }

    /** Returns true if the given rank settings are the same */
    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof Ranking)) return false;

        Ranking other=(Ranking)o;
        if (this.filter != other.filter) return false;
        if (this.literal != other.literal) return false;
        if (this.normal != other.normal) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(filter, literal, normal);
    }

    @Override
    public String toString() {
        return "rank settings [filter: " + filter + ", literal: " + literal + ", normal: "+normal+"]";
    }

    @Override
    public Ranking clone() {
        try {
            return (Ranking)super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Programming error", e);
        }
    }

}
