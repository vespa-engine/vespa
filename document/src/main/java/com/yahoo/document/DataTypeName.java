// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.text.Utf8Array;
import com.yahoo.text.Utf8String;

/**
 * A full document type name. The name is case sensitive. This is a <i>value object</i>.
 *
 * @author bratseth
 */
public final class DataTypeName {

    private final Utf8String name;

    /**
     * Creates a document name from a string of the form "name"
     *
     * @param name The name string to parse.
     * @throws NumberFormatException if the version part of the name is present but is not a number
     */
    public DataTypeName(String name) {
        this.name = new Utf8String(name);
    }
    public DataTypeName(Utf8Array name) {
        this.name = new Utf8String(name);
    }
    public DataTypeName(Utf8String name) {
        this.name = new Utf8String(name);
    }

    public String getName() { return name.toString(); }

    @Override
    public String toString() { return name.toString(); }

    @Override
    public int hashCode() { return name.hashCode(); }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DataTypeName)) return false;
        DataTypeName datatype = (DataTypeName)obj;
        return this.name.equals(datatype.name);
    }

}
