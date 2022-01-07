// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * @author hakonhall
 */
public final class ObjectInserter implements Inserter {

    private Cursor target;
    private String key;

    public ObjectInserter(Cursor c, String key) {
        target = c;
        this.key = key;
    }

    public final ObjectInserter adjust(Cursor c, String key) {
        target = c;
        this.key = key;
        return this;
    }

    public final Cursor insertNIX()                { return target.setNix(key); }
    public final Cursor insertBOOL(boolean value)  { return target.setBool(key, value); }
    public final Cursor insertLONG(long value)     { return target.setLong(key, value); }
    public final Cursor insertDOUBLE(double value) { return target.setDouble(key, value); }
    public final Cursor insertSTRING(String value) { return target.setString(key, value); }
    public final Cursor insertSTRING(byte[] utf8)  { return target.setString(key, utf8); }
    public final Cursor insertDATA(byte[] value)   { return target.setData(key, value); }
    public final Cursor insertARRAY()              { return target.setArray(key); }
    public final Cursor insertOBJECT()             { return target.setObject(key); }
}
