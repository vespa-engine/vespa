// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.metrics;

import com.yahoo.text.XMLWriter;
import com.yahoo.text.Utf8String;

/**
 * @author thomasg
 */
public class AverageMetric extends Metric {
    double sum = 0;
    double min = 0;
    double max = 0;
    int count = 0;


    public AverageMetric(String name, MetricSet owner) {
        super(name);
        owner.addMetric(this);
    }

    public void addValue(double value) {
        sum += value;
        count++;

        if (min == 0 || value < min) {
            min = value;
        }
        if (max == 0 || value > max) {
            max = value;
        }

    }

    static private final Utf8String attrValue = new Utf8String("value");
    static private final Utf8String attrCount = new Utf8String("count");
    static private final Utf8String attrMin = new Utf8String("min");
    static private final Utf8String attrMax = new Utf8String("max");

    @Override
    public void toXML(XMLWriter writer) {
        renderXmlName(writer);

        if (count > 0) {
            writer.attribute(attrValue, (sum / count));
            writer.attribute(attrCount, count);
            writer.attribute(attrMin, min);
            writer.attribute(attrMax, max);
        }
        writer.closeTag();
    }
}
