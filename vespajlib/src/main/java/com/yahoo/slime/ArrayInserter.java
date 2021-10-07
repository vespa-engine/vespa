// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * Helper class for inserting values into an ArrayValue.
 * For justification read Inserter documentation.
 **/
public final class ArrayInserter implements Inserter {
    private Cursor target;

    public ArrayInserter(Cursor c) { target = c; }

    public final ArrayInserter adjust(Cursor c) {
        target = c;
        return this;
    }

    public final Cursor insertNIX()                { return target.addNix(); }
    public final Cursor insertBOOL(boolean value)  { return target.addBool(value); }
    public final Cursor insertLONG(long value)     { return target.addLong(value); }
    public final Cursor insertDOUBLE(double value) { return target.addDouble(value); }
    public final Cursor insertSTRING(String value) { return target.addString(value); }
    public final Cursor insertSTRING(byte[] utf8)  { return target.addString(utf8); }
    public final Cursor insertDATA(byte[] value)   { return target.addData(value); }
    public final Cursor insertARRAY()              { return target.addArray(); }
    public final Cursor insertOBJECT()             { return target.addObject(); }
}
