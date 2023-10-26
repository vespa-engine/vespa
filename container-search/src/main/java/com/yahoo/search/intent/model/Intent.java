// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.intent.model;

/**
 * A representation of an intent behind a query. Intents have no structure but are just id's of a
 * set which is predefined in the application.
 * <p>
 * Intents are Value Objects.
 * <p>
 * Intent ids should be human readable, start with lower case and use camel casing
 *
 * @author bratseth
 */
public class Intent {

    private String id;

    public static final Intent Default=new Intent("default");

    /** Creates an intent from a string id */
    public Intent(String id) {
        this.id=id;
    }

    /** Returns the id of this intent, never null */
    public String getId() { return id; }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public boolean equals(Object other) {
        if (other==this) return true;
        if ( ! (other instanceof Intent)) return false;
        return this.id.equals(((Intent)other).id);
    }

    /** Returns the id of this intent */
    @Override
    public String toString() { return id; }

}
