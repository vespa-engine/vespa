// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import java.util.Optional;

/**
 * Helper class for inserting values into an ObjectValue.
 * For justification read Inserter documentation.
 **/
public final class ObjectInserter implements Inserter {
    private Cursor target;
    private int symbol;
    private Optional<String> symbolName = Optional.empty();

    public final ObjectInserter adjust(Cursor c, int sym) {
        target = c;
        symbol = sym;
        symbolName = Optional.empty();
        return this;
    }

    public final ObjectInserter adjust(Cursor c, String name) {
        target = c;
        symbol = -1;
        symbolName = Optional.of(name);
        return this;
    }

    public final Cursor insertNIX()                {
        return symbolName.map(name -> target.setNix(name))
                     .orElseGet(() -> target.setNix(symbol));
    }

    public final Cursor insertBOOL(boolean value)  {
        return symbolName.map(name -> target.setBool(name, value))
                     .orElseGet(() -> target.setBool(symbol, value));
    }

    public final Cursor insertLONG(long value)  {
        return symbolName.map(name -> target.setLong(name, value))
                     .orElseGet(() -> target.setLong(symbol, value));
    }

    public final Cursor insertDOUBLE(double value)  {
        return symbolName.map(name -> target.setDouble(name, value))
                     .orElseGet(() -> target.setDouble(symbol, value));
    }

    public final Cursor insertSTRING(String value)  {
        return symbolName.map(name -> target.setString(name, value))
                     .orElseGet(() -> target.setString(symbol, value));
    }

    public final Cursor insertSTRING(byte[] utf8)  {
        return symbolName.map(name -> target.setString(name, utf8))
                     .orElseGet(() -> target.setString(symbol, utf8));
    }

    public final Cursor insertDATA(byte[] value)  {
        return symbolName.map(name -> target.setData(name, value))
                     .orElseGet(() -> target.setData(symbol, value));
    }

    public final Cursor insertARRAY()  {
        return symbolName.map(name -> target.setArray(name))
                     .orElseGet(() -> target.setArray(symbol));
    }

    public final Cursor insertOBJECT()  {
        return symbolName.map(name -> target.setObject(name))
                     .orElseGet(() -> target.setObject(symbol));
    }
}
