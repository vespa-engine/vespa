// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * Helper class for inserting values into an ArrayValue.
 * For justification read Inserter documentation.
 */
public final class ArrayInserter implements Inserter {

    private Cursor target;

    public ArrayInserter(Cursor c) { target = c; }

    public ArrayInserter adjust(Cursor c) {
        target = c;
        return this;
    }

    public Cursor insertNIX()                { return target.addNix(); }
    public Cursor insertBOOL(boolean value)  { return target.addBool(value); }
    public Cursor insertLONG(long value)     { return target.addLong(value); }
    public Cursor insertDOUBLE(double value) { return target.addDouble(value); }
    public Cursor insertSTRING(String value) { return target.addString(value); }
    public Cursor insertSTRING(byte[] utf8)  { return target.addString(utf8); }
    public Cursor insertDATA(byte[] value)   { return target.addData(value); }
    public Cursor insertARRAY()              { return target.addArray(); }
    public Cursor insertOBJECT()             { return target.addObject(); }

}
