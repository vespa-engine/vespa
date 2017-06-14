// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.metrics;

import com.yahoo.text.XMLWriter;
import com.yahoo.text.Utf8String;

/**
 * @author thomasg
 */
public abstract class NumberMetric<V extends Number> extends Metric {
    private V value;

    public NumberMetric(String name, V v, MetricSet owner) {
        super(name);
        value = v;
        owner.addMetric(this);
    }

    public V get() {
        return value;
    }

    public void set(V value) {
        this.value = value;
    }

    public String toString() {
        return value.toString();
    }

    static private final Utf8String attrValue = new Utf8String("value");

    public void toXML(XMLWriter writer) {
        renderXmlName(writer);
        writer.attribute(attrValue, value);
        writer.closeTag();
    }


}
