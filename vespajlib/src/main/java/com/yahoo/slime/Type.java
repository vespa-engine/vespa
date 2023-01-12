// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * Enumeration of all possible Slime data types.
 */
public enum Type {

    NIX(0),
    BOOL(1),
    LONG(2),
    DOUBLE(3),
    STRING(4),
    DATA(5),
    ARRAY(6),
    OBJECT(7);

    public final byte ID;
    Type(int id) { this.ID = (byte)id; }

    private static final Type[] types = values();
    static Type asType(int id) { return types[id]; }

}
