// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.yahoo.api.annotations.Beta;
import com.yahoo.collections.Tuple2;
import com.yahoo.jdisc.Metric.Context;

/**
 * An efficiently comparable point in a sparse vector space.
 *
 * @author steinar
 */
@Beta
public final class Point implements Context {

    private final Value[] location;
    private final String[] dimensions;
    private final int hashCode;

    public Point(Map<String, ?> properties) {
        this(buildParameters(properties));
    }

    private Point(Tuple2<String[], Value[]> dimensionsAndLocation) {
        this(dimensionsAndLocation.first, dimensionsAndLocation.second);
    }

    /**
     * Only to be used by simplemetrics itself.
     *
     * @param dimensions dimension name, Point takes ownership of the array
     * @param location dimension values, Point takes ownership of the array
     */
    Point(String[] dimensions, Value[] location) {
        this.dimensions = dimensions;
        this.location = location;
        this.hashCode = Arrays.hashCode(location) * 31 + Arrays.hashCode(dimensions);
    }

    private static final Point theEmptyPoint = new Point(new String[0], new Value[0]);

    /** the canonical 0-dimensional Point. */
    public static Point emptyPoint() { return theEmptyPoint; }

    private static Tuple2<String[], Value[]> buildParameters(Map<String, ?> properties) {
        String[] dimensions = properties.keySet().toArray(new String[0]);
        Arrays.sort(dimensions);
        Value[] location = new Value[dimensions.length];
        for (int i = 0; i < dimensions.length; ++i) {
            location[i] = Value.of(String.valueOf(properties.get(dimensions[i])));
        }
        return new Tuple2<>(dimensions, location);

    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Point other = (Point) obj;
        return Arrays.equals(dimensions, other.dimensions) && Arrays.equals(location, other.location);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        final int maxLen = 3;
        StringBuilder builder = new StringBuilder();
        builder.append("Point [location=")
                .append(Arrays.asList(location).subList(0, Math.min(location.length, maxLen)))
                .append(", dimensions=")
                .append(Arrays.asList(dimensions).subList(0, Math.min(dimensions.length, maxLen)))
                .append("]");
        return builder.toString();
    }

    /**
     * Get an immutable list view of the values for each dimension.
     */
    public List<Value> location() {
        return List.of(location);
    }

    /**
     * Get an immutable list view of the names of each dimension.
     */
    public List<String> dimensions() {
        return List.of(dimensions);
    }

    /**
     * Get the number of dimensions defined for this Point, i.e. the size of the
     * collection returned by {@link #dimensions()}.
     */
    public int dimensionality() {
        return dimensions.length;
    }

    /** package private accessor only for simplemetrics itself */
    String[] getDimensions() {
        return dimensions;
    }

    /** package private accessor only for simplemetrics itself */
    Value[] getLocation() {
        return location;
    }

}
