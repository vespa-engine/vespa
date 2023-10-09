// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Inspectable;
import com.yahoo.data.access.simple.JsonRender;
import com.yahoo.data.JsonProducer;
import com.yahoo.data.XmlProducer;
import com.yahoo.prelude.hitfield.XmlRenderer;

/**
 * A wrapper for structured data representing feature values.
 */
public class StructuredData implements Inspectable, JsonProducer, XmlProducer {

    private final Inspector value;

    public StructuredData(Inspector value) {
        this.value = value;
    }

    @Override
    public Inspector inspect() {
        return value;
    }

    public String toString() {
        return toXML();
    }

    @Override
    public StringBuilder writeXML(StringBuilder target) {
        return XmlRenderer.render(target, value);
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
