// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.schema.document;

/**
 * Represents settings for dictionary control
 *
 * @author baldersheim
 */
public class Dictionary {
    public enum Type { BTREE, HASH, BTREE_AND_HASH };
    private Type type = null;
    private Case casing= null;

    public void updateType(Type type) {
        if (this.type == null) {
            this.type = type;
        } else if ((this.type == Type.BTREE) && (type == Type.HASH)) {
            this.type = Type.BTREE_AND_HASH;
        } else if ((this.type == Type.HASH) && (type == Type.BTREE)) {
            this.type = Type.BTREE_AND_HASH;
        } else {
            throw new IllegalArgumentException("Can not combine previous dictionary setting " + this.type +
                    " with current " + type);
        }
    }
    public void updateMatch(Case casing) {
        if (this.casing != null) {
            throw new IllegalArgumentException("dictionary match mode has already been set to " + this.casing);
        }
        this.casing = casing;
    }
    public Type getType() { return (type != null) ? type : Type.BTREE; }
    public Case getMatch() { return (casing != null) ? casing : Case.UNCASED; }
}
