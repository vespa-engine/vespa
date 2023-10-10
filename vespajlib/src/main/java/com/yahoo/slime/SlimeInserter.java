// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * Helper class for inserting values into a Slime object.
 * For justification read Inserter documentation.
 */
public final class SlimeInserter implements Inserter {

    private Slime target;

    public SlimeInserter(Slime target) {
        this.target = target;
    }

    public final SlimeInserter adjust(Slime slime) {
        target = slime;
        return this;
    }

    public final Cursor insertNIX()                { return target.setNix(); }
    public final Cursor insertBOOL(boolean value)  { return target.setBool(value); }
    public final Cursor insertLONG(long value)     { return target.setLong(value); }
    public final Cursor insertDOUBLE(double value) { return target.setDouble(value); }
    public final Cursor insertSTRING(String value) { return target.setString(value); }
    public final Cursor insertSTRING(byte[] utf8)  { return target.setString(utf8); }
    public final Cursor insertDATA(byte[] value)   { return target.setData(value); }
    public final Cursor insertARRAY()              { return target.setArray(); }
    public final Cursor insertOBJECT()             { return target.setObject(); }

}
