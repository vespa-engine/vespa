// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Inspectable;
import com.yahoo.data.access.Type;
import com.yahoo.data.JsonProducer;
import com.yahoo.data.access.simple.JsonRender;

/**
 * A wrapper for structured data representing feature values.
 */
public class FeatureData implements Inspectable, JsonProducer {

    private final Inspector value;

    public FeatureData(Inspector value) {
        this.value = value;
    }

    @Override
    public Inspector inspect() {
        return value;
    }

    public String toString() {
        if (value.type() == Type.EMPTY) {
            return "";
        } else {
            return toJson();
        }
    }

    @Override
    public String toJson() {
        return writeJson(new StringBuilder()).toString();
    }

    @Override
    public StringBuilder writeJson(StringBuilder target) {
        return JsonRender.render(value, target, true);
    }

}
