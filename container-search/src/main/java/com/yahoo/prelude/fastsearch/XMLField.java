// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.prelude.fastsearch;

import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Type;
import com.yahoo.prelude.hitfield.XMLString;
import com.yahoo.search.result.PositionsData;

/**
 * Class converting data (historically XML-encoded) from a document summary field.
 * This has only been used to represent geographical positions.
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
        if (value.type() == Type.OBJECT || value.type() == Type.ARRAY) {
            return new PositionsData(value);
        }
        return convert(value.asString(""));
    }
        
}
