// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchdefinition.document;

/**
 * Represents settings for dictionary control
 *
 * @author baldersheim
 */
public class Dictionary {
    public enum Type { BTREE, HASH, BTREE_AND_HASH };
    private final Type type;
    public Dictionary() { this(Type.BTREE); }
    public Dictionary(Type type) { this.type = type; }
    public Type getType() { return type; }
}
