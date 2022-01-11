// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple.jdisc;

import com.yahoo.collections.Tuple2;
import com.yahoo.container.jdisc.state.CountMetric;
import com.yahoo.container.jdisc.state.GaugeMetric;
import com.yahoo.container.jdisc.state.MetricDimensions;
import com.yahoo.container.jdisc.state.MetricSet;
import com.yahoo.container.jdisc.state.MetricSnapshot;
import com.yahoo.container.jdisc.state.MetricValue;
import com.yahoo.container.jdisc.state.StateMetricContext;
import com.yahoo.metrics.simple.Bucket;
import com.yahoo.metrics.simple.Identifier;
import com.yahoo.metrics.simple.Point;
import com.yahoo.metrics.simple.UntypedMetric;
import com.yahoo.metrics.simple.UntypedMetric.Histogram;
import com.yahoo.metrics.simple.Value;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Convert simple metrics snapshots into jdisc state snapshots.
 *
 * @author arnej27959
 */
class SnapshotConverter {

    private static Logger log = Logger.getLogger(SnapshotConverter.class.getName());

    final Bucket snapshot;
    final Map<Point, Map<String, MetricValue>> perPointData = new HashMap<>();
    private static final char[] DIGITS = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    public SnapshotConverter(Bucket snapshot) {
        this.snapshot = snapshot;
    }

    static MetricDimensions convert(Point p) {
        if (p == null) {
            return StateMetricContext.newInstance(null);
        }
        List<String> dimensions = p.dimensions();
        List<Value> location = p.location();
        Map<String, Object> pointWrapper = new HashMap<>(dimensions.size());
        for (int i = 0; i < dimensions.size(); ++i) {
            pointWrapper.put(dimensions.get(i), valueAsString(location.get(i)));
        }
        return StateMetricContext.newInstance(pointWrapper);
    }

    // TODO: just a compatibility wrapper, should be removed ASAP
    private static Object valueAsString(Value value) {
        switch (value.getType()) {
            case STRING:
                return value.stringValue();
            case LONG:
                return value.longValue();
            case DOUBLE:
                return value.doubleValue();
            default:
                throw new IllegalStateException("simplemetrics impl is out of sync with itself, please file a ticket.");
        }
    }

    static MetricValue convert(UntypedMetric val) {
        if (val.isCounter()) {
            return CountMetric.newInstance(val.getCount());
        } else {
            if (val.getHistogram() == null) {
                return GaugeMetric.newInstance(val.getLast(), val.getMax(), val.getMin(), val.getSum(), val.getCount());
            } else {
                return GaugeMetric.newInstance(val.getLast(), val.getMax(), val.getMin(), val.getSum(), val.getCount(),
                        Optional.of(buildPercentileList(val.getHistogram())));
            }
        }
    }

    private static List<Tuple2<String, Double>> buildPercentileList(Histogram histogram) {
        List<Tuple2<String, Double>> prefixAndValues = new ArrayList<>(2);
        prefixAndValues.add(new Tuple2<>("95", histogram.getValueAtPercentile(95.0d)));
        prefixAndValues.add(new Tuple2<>("99", histogram.getValueAtPercentile(99.0d)));
        return prefixAndValues;
    }

    MetricSnapshot convert() {
        for (Map.Entry<Identifier, UntypedMetric> entry : snapshot.entrySet()) {
            Identifier ident = entry.getKey();
            getMap(ident.getLocation()).put(ident.getName(), convert(entry.getValue()));
        }
        Map<MetricDimensions, MetricSet> data = new HashMap<>();
        for (Map.Entry<Point, Map<String, MetricValue>> entry : perPointData.entrySet()) {
            MetricDimensions key = convert(entry.getKey());
            MetricSet newval = new MetricSet(entry.getValue());
            MetricSet old = data.get(key);
            if (old != null) {
                // should not happen, this is bad
                // TODO: consider merging the two MetricSet instances
                log.warning("losing MetricSet when converting for: "+entry.getKey());
            } else {
                data.put(key, newval);
            }
        }
        return new MetricSnapshot(snapshot.getFromMillis(),
                                  snapshot.getToMillis(),
                                  TimeUnit.MILLISECONDS,
                                  data);
    }

    private Map<String, MetricValue> getMap(Point point) {
        if (point == null) {
            point = Point.emptyPoint();
        }
        if (! perPointData.containsKey(point)) {
            perPointData.put(point, new HashMap<>());
        }
        return perPointData.get(point);
    }

    void outputHistograms(PrintStream output) {
        boolean gotHistogram = false;
        for (Map.Entry<Identifier, UntypedMetric> entry : snapshot.entrySet()) {
            if (entry.getValue().getHistogram() == null) {
                continue;
            }
            gotHistogram = true;
            Histogram histogram = entry.getValue().getHistogram();
            Identifier id = entry.getKey();
            String metricIdentifier = getIdentifierString(id);
            output.println("# start of metric " + metricIdentifier);
            histogram.outputPercentileDistribution(output, 4, 1.0d, true);
            output.println("# end of metric " + metricIdentifier);
        }
        if (!gotHistogram) {
            output.println("# No histograms currently available.");
        }
    }

    private String getIdentifierString(Identifier id) {
        StringBuilder buffer = new StringBuilder();
        Point location = id.getLocation();
        buffer.append(id.getName());
        if (location != null) {
            buffer.append(", dimensions: { ");
            Iterator<String> dimensions = location.dimensions().iterator();
            Iterator<Value> values = location.location().iterator();
            boolean firstDimension = true;
            while (dimensions.hasNext() && values.hasNext()) {

                if (firstDimension) {
                    firstDimension = false;
                } else {
                    buffer.append(", ");
                }
                serializeSingleDimension(buffer, dimensions.next(), values.next());
            }
            buffer.append(" }");
        }
        return buffer.toString();

    }

    private void serializeSingleDimension(StringBuilder buffer, final String dimensionName, Value dimensionValue) {
        buffer.append('"');
        escape(dimensionName, buffer);
        buffer.append("\": ");
        switch (dimensionValue.getType()) {
        case LONG:
            buffer.append(Long.toString(dimensionValue.longValue()));
            break;
        case DOUBLE:
            buffer.append(Double.toString(dimensionValue.doubleValue()));
            break;
        case STRING:
            buffer.append('"');
            escape(dimensionValue.stringValue(), buffer);
            buffer.append('"');
            break;
        default:
            buffer.append("\"Unknown type for this dimension, this is a bug.\"");
            break;
        }
    }

    private void escape(final String in, final StringBuilder target) {
        for (final char c : in.toCharArray()) {
            switch (c) {
            case ('"'):
                target.append("\\\"");
                break;
            case ('\\'):
                target.append("\\\\");
                break;
            case ('\b'):
                target.append("\\b");
                break;
            case ('\f'):
                target.append("\\f");
                break;
            case ('\n'):
                target.append("\\n");
                break;
            case ('\r'):
                target.append("\\r");
                break;
            case ('\t'):
                target.append("\\t");
                break;
            default:
                if (c < 32) {
                    target.append("\\u").append(fourDigitHexString(c));
                } else {
                    target.append(c);
                }
                break;
            }
        }
    }

    private static char[] fourDigitHexString(final char c) {
        final char[] hex = new char[4];
        int in = ((c) & 0xFFFF);
        for (int i = 3; i >= 0; --i) {
            hex[i] = DIGITS[in & 0xF];
            in >>>= 4;
        }
        return hex;
    }
}
