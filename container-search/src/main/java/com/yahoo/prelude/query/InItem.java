// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.util.Objects;

import static java.util.Objects.requireNonNullElse;

/*
 * Abstract class representing an IN operator.
 *
 * @author toregge
 */
public abstract class InItem extends Item {
    private String indexName;
    public InItem(String indexName) {
        this.indexName = requireNonNullElse(indexName, "");
    }

    @Override
    public void setIndexName(String index) {
        this.indexName = requireNonNullElse(index, "");
    }
    public String getIndexName() {
        return indexName;
    }

    @Override
    public String getName() {
        return getItemType().name();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! super.equals(o)) return false;
        var other = (InItem)o;
        if ( ! Objects.equals(this.indexName, other.indexName)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), indexName);
    }

};
