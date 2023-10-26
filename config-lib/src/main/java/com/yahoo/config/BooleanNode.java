// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

/**
 * The BooleanNode class represents a boolean in a configuration tree.
 */
public class BooleanNode extends LeafNode<Boolean> {

    public BooleanNode() {
    }

    public BooleanNode(boolean value) {
        super(true);
        this.value = value;
    }

    public Boolean value() {
        return value;
    }

    @Override
    public String getValue() {
        return "" + value;
    }

    @Override
    public String toString() {
        return getValue();
    }

    @Override
    protected boolean doSetValue(String value) {
        if (! value.equalsIgnoreCase("false") && ! value.equalsIgnoreCase("true")) {
            return false;
        }
        this.value = Boolean.valueOf(value);
        return true;
    }

    @Override
    void serialize(String name, Serializer serializer) {
        serializer.serialize(name, value);
    }

    @Override
    void serialize(Serializer serializer) {
        serializer.serialize(value);
    }
}
