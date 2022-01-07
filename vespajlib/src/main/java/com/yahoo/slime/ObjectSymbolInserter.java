// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * Helper class for inserting values into an ObjectValue.
 * For justification read Inserter documentation.
 */
public final class ObjectSymbolInserter implements Inserter {

    private Cursor target;
    private int symbol;

    public ObjectSymbolInserter(Cursor cursor, int sym) {
        target = cursor;
        symbol = sym;
    }

    public ObjectSymbolInserter adjust(Cursor c, int sym) {
        target = c;
        symbol = sym;
        return this;
    }

    public Cursor insertNIX()                { return target.setNix(symbol); }
    public Cursor insertBOOL(boolean value)  { return target.setBool(symbol, value); }
    public Cursor insertLONG(long value)     { return target.setLong(symbol, value); }
    public Cursor insertDOUBLE(double value) { return target.setDouble(symbol, value); }
    public Cursor insertSTRING(String value) { return target.setString(symbol, value); }
    public Cursor insertSTRING(byte[] utf8)  { return target.setString(symbol, utf8); }
    public Cursor insertDATA(byte[] value)   { return target.setData(symbol, value); }
    public Cursor insertARRAY()              { return target.setArray(symbol); }
    public Cursor insertOBJECT()             { return target.setObject(symbol); }
}
