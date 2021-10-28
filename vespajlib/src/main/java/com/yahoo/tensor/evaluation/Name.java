// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.evaluation;

/** A name which is just a string. Names are value objects. */
public class Name {

    private final String name;

    public Name(String name) {
        this.name = name;
    }

    public String name() { return name; }

    @Override
    public String toString() { return name; }

    @Override
    public int hashCode() { return name.hashCode(); }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof Name)) return false;
        return ((Name)other).name.equals(this.name);
    }

}
