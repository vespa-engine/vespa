// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.intent.model;

/**
 * A representation of a source. Sources have no structure but are just id of a
 * set which is defined in the application.
 * <p>
 * Sources are Value Objects.
 * <p>
 * Source ids should be human readable, start with lower case and use camel casing
 *
 * @author bratseth
 */
public class Source {

    private String id;

    /** Creates an intent from a string id */
    public Source(String id) {
        this.id=id;
    }

    /** Returns the id of this source, never null */
    public String getId() { return id; }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public boolean equals(Object other) {
        if (other==this) return true;
        if ( ! (other instanceof Source)) return false;
        return this.id.equals(((Source)other).id);
    }

    /** Returns the id of this source */
    @Override
    public String toString() { return id; }

}
