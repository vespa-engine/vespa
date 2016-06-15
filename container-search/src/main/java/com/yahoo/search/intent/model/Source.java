// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.intent.model;

/**
 * A representation of a source. Sources have no structure but are just id of a
 * set which is defined in the application.
 * <p>
 * Sources are Value Objects.
 * <p>
 * Source ids should be human readable, start with lower case and use camel casing
 *
 * @author <a href="mailto:bratseth@yahoo-inc.com">Jon Bratseth</a>
 */
public class Source {

    private String id;

    /** Creates an intent from a string id */
    public Source(String id) {
        this.id=id;
    }

    /** Returns the id of this source, never null */
    public String getId() { return id; }

    public @Override int hashCode() { return id.hashCode(); }

    public @Override boolean equals(Object other) {
        if (other==this) return true;
        if ( ! (other instanceof Source)) return false;
        return this.id.equals(((Source)other).id);
    }

    /** Returns the id of this source */
    public @Override String toString() { return id; }

}
