// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import java.util.ArrayList;
import java.util.Collections;

import com.yahoo.api.annotations.Beta;

/**
 * Single-use builder for the immutable Point instances used to set dimensions
 * for a metric. Get a fresh instance either from a corresponding Gauge or Counter,
 * or through the MetricReceiver API.
 *
 * @author steinar
 */
@Beta
public final class PointBuilder {
    private ArrayList<String> dimensions;
    private ArrayList<Value> location;

    public enum Discriminator {
        LONG, DOUBLE, STRING;
    }

    PointBuilder() {
        this(null);
    }

    PointBuilder(Point p) {
        if (p != null) {
            int size = p.dimensionality();
            dimensions = new ArrayList<>(size+2);
            location = new ArrayList<>(size+2);
            Collections.addAll(dimensions, p.getDimensions());
            Collections.addAll(location, p.getLocation());
        } else {
            dimensions = new ArrayList<>(4);
            location = new ArrayList<>(4);
        }
    }

    /**
     * Set a named dimension to an integer value.
     *
     * @param dimensionName the name of the dimension to set
     * @param dimensionValue to value for the given dimension
     * @return this, to facilitate chaining
     */
    public PointBuilder set(String dimensionName, long dimensionValue) {
        return set(dimensionName, Value.of(dimensionValue));
    }

    /**
     * Set a named dimension to a floating point value.
     *
     * @param dimensionName the name of the dimension to set
     * @param dimensionValue to value for the given dimension
     * @return this, to facilitate chaining
     */
    public PointBuilder set(String dimensionName, double dimensionValue) {
        return set(dimensionName, Value.of(dimensionValue));
    }

    /**
     * Set a named dimension to a string value.
     *
     * @param dimensionName the name of the dimension to set
     * @param dimensionValue to value for the given dimension
     * @return this, to facilitate chaining
     */
    public PointBuilder set(String dimensionName, String dimensionValue) {
        return set(dimensionName, Value.of(dimensionValue));
    }

    private PointBuilder set(String axisName, Value w) {
        // handle setting same axis multiple times nicely
        int i = Collections.binarySearch(dimensions, axisName);
        if (i < 0) {
            dimensions.add(~i, axisName);
            location.add(~i, w);
        } else {
            // only set location, dim obviously exists
            location.set(i, w);
        }
        return this;
    }

    /**
     * Create a new Point instance using the settings stored in this
     * PointBuilder. PointBuilder instances cannot be re-used after build() has
     * been invoked.
     *
     * @return a Point instance reflecting this builder
     */
    public Point build() {
        Point p = Point.emptyPoint();
        int size = dimensions.size();
        if (size != 0) {
            p = new Point(dimensions.toArray(new String[size]), location.toArray(new Value[size]));
        }
        // deny builder re-use
        dimensions = null;
        location = null;
        return p;
    }

    @Override
    public String toString() {
        final int maxLen = 3;
        StringBuilder builder = new StringBuilder();
        builder.append("PointBuilder [dimensions=")
                .append(dimensions != null ? dimensions.subList(0, Math.min(dimensions.size(), maxLen)) : null)
                .append(", location=").append(location != null ? location.subList(0, Math.min(location.size(), maxLen)) : null)
                .append("]");
        return builder.toString();
    }
}
