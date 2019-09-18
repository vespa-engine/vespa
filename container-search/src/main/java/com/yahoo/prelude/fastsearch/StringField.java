// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Class representing a string field in the result set
 *
 */
package com.yahoo.prelude.fastsearch;


import java.nio.ByteBuffer;

import com.yahoo.text.Utf8;
import com.yahoo.data.access.Inspector;


/**
 * @author Bjørn Borud
 */
public class StringField extends DocsumField {

    public StringField(String name) {
        super(name);
    }

    @Override
    public String toString() {
        return "field " + getName() + " type string";
    }

    @Override
    public Object convert(Inspector value) {
        return value.asString("");
    }

    boolean isString() { return true; }

}
