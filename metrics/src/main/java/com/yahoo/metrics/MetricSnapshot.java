// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

import com.yahoo.text.XMLWriter;
import com.yahoo.text.Utf8String;

import java.io.StringWriter;

public class MetricSnapshot
{
    String name;
    int period;
    int fromTime;
    int toTime;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPeriod() {
        return period;
    }

    public void setPeriod(int period) {
        this.period = period;
    }

    public int getFromTime() {
        return fromTime;
    }

    public void setFromTime(int fromTime) {
        this.fromTime = fromTime;
    }

    public int getToTime() {
        return toTime;
    }

    public void setToTime(int toTime) {
        this.toTime = toTime;
    }

    public MetricSet getMetrics() {
        return snapshot;
    }

    MetricSet snapshot;

    public MetricSnapshot(String name) {
        this.name = name;
        this.period = 0;
        this.fromTime = 0;
        this.toTime = 0;
        snapshot = new SimpleMetricSet("metrics", "", "");
    }

    public MetricSnapshot(String name,
                          int period,
                          MetricSet source,
                          boolean copyUnset) {
        this(name);
        this.period = period;
        snapshot = (MetricSet)source.clone(Metric.CopyType.INACTIVE, null, copyUnset);
    }

    void reset(int currentTime) {
        fromTime = currentTime;
        toTime = 0;
        snapshot.reset();
    }

    public void recreateSnapshot(MetricSet source, boolean copyUnset) {
        MetricSet newSnapshot = (MetricSet)source.clone(Metric.CopyType.INACTIVE, null, copyUnset);
        newSnapshot.reset();
        snapshot.addToSnapshot(newSnapshot);
        snapshot = newSnapshot;
    }

    public static final Utf8String TAG_NAME = new Utf8String("name");
    public static final Utf8String TAG_FROM = new Utf8String("from");
    public static final Utf8String TAG_TO   = new Utf8String("to");
    public static final Utf8String TAG_PERIOD = new Utf8String("period");

    public void printXml(MetricManager man, String consumer, int verbosity, XMLWriter writer) {
        writer.openTag("snapshot");
        writer.attribute(TAG_NAME, name);
        writer.attribute(TAG_FROM, fromTime);
        writer.attribute(TAG_TO, toTime);
        writer.attribute(TAG_PERIOD, period);

        for (Metric m : snapshot.getRegisteredMetrics()) {
            if (m instanceof MetricSet) {
                man.printXml((MetricSet)m, writer, toTime > fromTime ? (toTime - fromTime) : period, consumer, verbosity);
            }
        }

        writer.closeTag();
    }

    public String toXml(MetricManager man, String consumer, int verbosity) {
        StringWriter str = new StringWriter();
        XMLWriter writer = new XMLWriter(str);
        printXml(man, consumer, verbosity, writer);
        return str.toString();
    }

    public void addToSnapshot(MetricSnapshot other, int currentTime, boolean reset) {
        snapshot.addToSnapshot(other.getMetrics());
        if (reset) {
            reset(currentTime);
        }
        other.toTime = currentTime;
    }
}
