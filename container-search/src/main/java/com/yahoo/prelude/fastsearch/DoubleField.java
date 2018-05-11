// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;


import java.nio.ByteBuffer;

import com.yahoo.search.result.NanNumber;
import com.yahoo.data.access.Inspector;

/**
 * @author Mathias Mølster Lidal
 */
public class DoubleField extends DocsumField {

    static final double EMPTY_VALUE = Double.NaN;

    public DoubleField(String name) {
        super(name);
    }

    private Object convert(double value) {
        if (Double.isNaN(value)) {
            return NanNumber.NaN;
        } else {
            return Double.valueOf(value);
        }
    }

    @Override
    public Object convert(Inspector value) {
        return convert(value.asDouble(EMPTY_VALUE));
    }

}
