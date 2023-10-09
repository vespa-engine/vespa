// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.data.access.Inspector;
import com.yahoo.search.result.NanNumber;

/**
 * A 16-bit float, represented as a (32-bit) Float in Java, as there is no 16-bit float support.
 *
 * @author bratseth
 */
public class Float16Field extends DocsumField {

    static final double EMPTY_VALUE = Float.NaN;

    public Float16Field(String name) {
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
