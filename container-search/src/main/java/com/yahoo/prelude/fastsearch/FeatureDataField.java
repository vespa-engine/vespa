// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Type;
import com.yahoo.search.result.FeatureData;

/**
 * Class representing a "feature data" field: A map of values which are
 * either floats or tensors.
 */
public class FeatureDataField extends LongstringField {

    public FeatureDataField(String name) {
        super(name);
    }

    @Override
    public String toString() {
        return "field " + getName() + " type FeatureDataField";
    }

    @Override
    public Object convert(Inspector value) {
        if ( ! value.valid()) return null;
        if (value.type() == Type.STRING) return value.asString();
        return new FeatureData(value);
    }

}
