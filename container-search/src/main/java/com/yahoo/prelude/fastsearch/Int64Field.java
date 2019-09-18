// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Class representing a integer field in the result set
 *
 */
package com.yahoo.prelude.fastsearch;


import java.nio.ByteBuffer;

import com.yahoo.search.result.NanNumber;
import com.yahoo.data.access.Inspector;


/**
 * @author Bjørn Borud
 */
public class Int64Field extends DocsumField {

    static final long EMPTY_VALUE = Long.MIN_VALUE;

    public Int64Field(String name) {
        super(name);
    }

    private Object convert(long value) {
        if (value == EMPTY_VALUE) {
            return NanNumber.NaN;
        } else {
            return Long.valueOf(value);
        }
    }

    @Override
    public String toString() {
        return "field " + getName() + " type int64";
    }

    @Override
    public Object convert(Inspector value) {
        return convert(value.asLong(EMPTY_VALUE));
    }

}
