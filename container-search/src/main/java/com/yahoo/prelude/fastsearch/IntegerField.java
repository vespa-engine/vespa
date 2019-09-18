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
public class IntegerField extends DocsumField {

    static final int EMPTY_VALUE = Integer.MIN_VALUE;

    public IntegerField(String name) {
        super(name);
    }

    private Object convert(int value) {
        if (value == EMPTY_VALUE) {
            return NanNumber.NaN;
        } else {
            return value;
        }
    }

    @Override
    public String toString() {
        return "field " + getName() + " type int";
    }

    @Override
    public Object convert(Inspector value) {
        return convert((int)value.asLong(EMPTY_VALUE));
    }

}
