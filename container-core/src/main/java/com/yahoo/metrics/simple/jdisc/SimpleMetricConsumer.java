// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple.jdisc;

import java.util.Map;

import com.yahoo.jdisc.Metric.Context;
import com.yahoo.jdisc.application.MetricConsumer;
import com.yahoo.metrics.simple.Identifier;
import com.yahoo.metrics.simple.Measurement;
import com.yahoo.metrics.simple.Point;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.metrics.simple.Sample;
import com.yahoo.metrics.simple.UntypedMetric.AssumedType;

/**
 * The metrics consumer in JDisc.
 *
 * @author Steinar Knutsen
 */
public class SimpleMetricConsumer implements MetricConsumer {

    private final MetricReceiver receiver;

    public SimpleMetricConsumer(MetricReceiver receiver) {
        this.receiver = receiver;
    }

    @Override
    public void set(String key, Number val, Context ctx) {
        receiver.update(new Sample(new Measurement(val), new Identifier(key, getSimpleCoordinate(ctx)), AssumedType.GAUGE));
    }

    @Override
    public void add(String key, Number val, Context ctx) {
        receiver.update(new Sample(new Measurement(val), new Identifier(key, getSimpleCoordinate(ctx)), AssumedType.COUNTER));
    }

    private Point getSimpleCoordinate(Context ctx) {
        if (ctx instanceof Point) {
            return (Point) ctx;
        } else {
            return null;
        }
    }

    @Override
    public Context createContext(Map<String, ?> properties) {
        if ((properties == null) || properties.isEmpty())
            return Point.emptyPoint();
        return new Point(properties);
    }

}
