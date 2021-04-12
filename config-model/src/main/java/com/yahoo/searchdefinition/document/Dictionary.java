// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchdefinition.document;

/**
 * Represents settings for dictionary control
 *
 * @author baldersheim
 */
public class Dictionary {
    public enum Type { BTREE, HASH, BTREE_AND_HASH };
    public enum Match { CASED, UNCASED };
    private Type type = null;
    private Match match = null;

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
    public void updateMatch(Match match) {
        if (this.match != null) {
            throw new IllegalArgumentException("dictionary match mode has already been set to " + this.match);
        }
        this.match = match;
    }
    public Type getType() { return (type != null) ? type : Type.BTREE; }
    public Match getMatch() { return (match != null) ? match : Match.UNCASED; }
}
