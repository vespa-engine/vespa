// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

/**
 * The DoubleNode class represents a double in a configuration tree.
 */
public class DoubleNode extends LeafNode<Double> {

    public DoubleNode() {
    }

    public DoubleNode(double value) {
        super(true);
        this.value = value;
    }

    public Double value() {
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
            this.value = Double.parseDouble(value);
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
