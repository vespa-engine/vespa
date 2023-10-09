// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

/**
 * The IntegerNode class represents an integer in a configuration tree.
 */
public class IntegerNode extends LeafNode<Integer> {

    public IntegerNode() {
    }

    public IntegerNode(int value) {
        super(true);
        this.value = value;
    }

    public Integer value() {
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
        try {
            this.value = Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
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
