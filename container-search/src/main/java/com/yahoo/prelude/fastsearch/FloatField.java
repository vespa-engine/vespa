// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;


import java.nio.ByteBuffer;

import com.yahoo.search.result.NanNumber;
import com.yahoo.data.access.Inspector;


/**
 * @author Mathias Mølster Lidal
 */
public class FloatField extends DocsumField {

    static final double EMPTY_VALUE = Float.NaN;

    public FloatField(String name) {
        super(name);
    }

    private Object convert(float value) {
        if (Float.isNaN(value)) {
            return NanNumber.NaN;
        } else {
            return Float.valueOf(value);
        }
    }

    @Override
    public Object convert(Inspector value) {
        return convert((float)value.asDouble(EMPTY_VALUE));
    }

}
