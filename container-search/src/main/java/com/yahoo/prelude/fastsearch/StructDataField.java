// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.search.result.StructuredData;
import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Type;
import com.yahoo.prelude.hitfield.JSONString;

/**
 * A hit field containing JSON structured data
 */
public class StructDataField extends DocsumField {

    public StructDataField(String name) {
        super(name);
    }

    @Override
    public String toString() {
        return "field " + getName() + " type StructDataField";
    }

    @Override
    public Object convert(Inspector value) {
        if (value.type() == Type.STRING)
            return convertString(value);
        return new StructuredData(value);
    }

    private Object convertString(Inspector value) {
        if (value.valid()) {
            return new JSONString(value);
        } else {
            return new JSONString("");
        }
    }

}
