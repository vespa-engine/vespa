// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents a long in a configuration tree.
 * @author gjoranv
 */
public class LongNode extends LeafNode<Long> {

    public LongNode() {
    }

    public LongNode(long value) {
        super(true);
        this.value = value;
    }

    public Long value() {
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
    protected boolean doSetValue(@NonNull String value) {
        try {
            this.value = Long.parseLong(value);
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
