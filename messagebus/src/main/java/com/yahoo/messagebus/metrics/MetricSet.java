// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.metrics;

import com.yahoo.text.XMLWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author thomasg
 */
public class MetricSet extends Metric {
    private List<Metric> metrics = new ArrayList<Metric>();

    public MetricSet(String name) {
        super(name);
    }

    public void addMetric(Metric m) {
        metrics.add(m);
    }

    public List<Metric> getMetrics() {
        return Collections.unmodifiableList(metrics);
    }

    public String toHTML() {
        StringBuilder builder = new StringBuilder();
        builder.append("<ul>\n");
        for (Metric m : metrics) {
            builder.append("<li>\n").append(m.toHTML()).append("\n</li>");
        }
        builder.append("\n</ul>\n");
        return builder.toString();
    }

    public void toXML(XMLWriter xmlWriter) {
        renderXmlName(xmlWriter);

        for (Metric m : metrics) {
            m.toXML(xmlWriter);
        }

        xmlWriter.closeTag();
    }
}
