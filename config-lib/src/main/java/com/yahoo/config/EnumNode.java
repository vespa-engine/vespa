// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

/**
 * The EnumNode class is a superclass for Enumerations in a configuration tree.
 */
public abstract class EnumNode<ENUM extends Enum<?>> extends LeafNode<ENUM> {

    public EnumNode() {
    }

    public EnumNode(boolean b) {
        super(b);
    }

    @Override
    public String toString() {
        return (value == null) ? "(null)" : value.toString();
    }

    @Override
    public String getValue() {
        return (value == null) ? null : value.toString();
    }
}
