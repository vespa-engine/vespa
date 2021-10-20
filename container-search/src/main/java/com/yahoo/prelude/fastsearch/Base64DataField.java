// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.simple.Value;
import com.yahoo.prelude.hitfield.RawBase64;

/**
 * Represents a binary field that is presented as base64
 * @author baldersheim
 */
public class Base64DataField extends DocsumField {
    public Base64DataField(String name) {
        super(name);
    }

    @Override
    public String toString() {
        return "field " + getName() + " type raw";
    }

    @Override
    public Object convert(Inspector value) {
        return new RawBase64(value.asData(Value.empty().asData()));
    }
}
