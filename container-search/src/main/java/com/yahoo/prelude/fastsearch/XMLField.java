// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Class representing a string field in the result set
 *
 */
package com.yahoo.prelude.fastsearch;


import java.nio.ByteBuffer;

import com.yahoo.io.SlowInflate;
import com.yahoo.prelude.hitfield.XMLString;
import com.yahoo.text.Utf8;
import com.yahoo.data.access.Inspector;


/**
 * @author Steinar Knutsen
 */
public class XMLField extends DocsumField {

    public XMLField(String name) {
        super(name);
    }

    private Object convert(String value) {
        return new XMLString(value);
    }

    @Override
    public String toString() {
        return "field " + getName() + " type XMLString";
    }

    @Override
    public Object convert(Inspector value) {
        return convert(value.asString(""));
    }
        
}
