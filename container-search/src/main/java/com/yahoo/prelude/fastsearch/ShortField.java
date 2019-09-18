// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Class representing a short field in the result set
 *
 */
package com.yahoo.prelude.fastsearch;


import java.nio.ByteBuffer;

import com.yahoo.search.result.NanNumber;
import com.yahoo.data.access.Inspector;

/**
 * @author Bjørn Borud
 */
public class ShortField extends DocsumField {

    static final short EMPTY_VALUE = Short.MIN_VALUE;

    public ShortField(String name) {
        super(name);
    }

    private Object convert(short value) {
        if (value == EMPTY_VALUE) {
            return NanNumber.NaN;
        } else {
            return Short.valueOf(value);
        }
    }        

    @Override
    public Object convert(Inspector value) {
        return convert((short)value.asLong(EMPTY_VALUE));
    }

}
