// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.model;

/**
 * The layout of a section
 *
 * @author bratseth
 */
// This is not made an enum, to allow the value set to be extendible.
// It is not explicitly made immutable
// to enable adding of internal state later (esp. parameters).
// If this becomes mutable, the creation scheme must be changed
// such that each fromString returns a unique instance, and
// the name must become a (immutable) type.
public class Layout {

    /** The built in "column" layout */
    public static final Layout column=new Layout("column");
    /** The built in "row" layout */
    public static final Layout row=new Layout("row");

    private String name;

    public Layout(String name) {
        this.name=name;
    }

    public String getName() { return name; }

    @Override
    public int hashCode() { return name.hashCode(); }

    @Override
    public boolean equals(Object o) {
        if (o==this) return true;
        if (! (o instanceof Layout)) return false;
        Layout other=(Layout)o;
        return this.name.equals(other.name);
    }

    /** Returns a layout having this string as name, or null if the given string is null or empty */
    public static Layout fromString(String layout) {
        //if (layout==null) return null;
        //if (layout)
        if (layout.equals("column")) return column;
        if (layout.equals("row")) return row;
        return new Layout(layout);
    }

    @Override
    public String toString() { return "layout '" + name + "'"; }

}
