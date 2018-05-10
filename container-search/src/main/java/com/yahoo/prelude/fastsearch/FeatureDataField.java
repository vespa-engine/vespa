// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Type;
import com.yahoo.container.search.LegacyEmulationConfig;
import com.yahoo.search.result.FeatureData;

/**
 * Class representing a "feature data" field.  This was historically
 * just a string containing JSON; now it's a structure of
 * data (that will be rendered as JSON by default).
 */
public class FeatureDataField extends LongstringField {

    public FeatureDataField (String name) {
        super(name);
    }

    @Override
    public String toString() {
        return "field " + getName() + " type FeatureDataField";
    }

    @Override
    public Object convert(Inspector value) {
        if (! value.valid()) {
            if (getEmulConfig().stringBackedFeatureData()) {
                return "";
            } else if (getEmulConfig().forceFillEmptyFields()) {
                return new FeatureData(com.yahoo.data.access.simple.Value.empty());
            } else {
                return null;
            }
        }
        if (value.type() == Type.STRING) {
            return value.asString();
        }
        FeatureData obj = new FeatureData(value);
        if (getEmulConfig().stringBackedFeatureData()) {
            return obj.toJson();
        } else {
            return obj;
        }
    }

}
