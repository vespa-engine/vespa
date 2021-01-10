// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An immutable set of dimension values.
 * Note that this may contain more or fewer values than needed given a set of dimensions.
 * Any missing values are treated as null.
 *
 * @author bratseth
 */
public class DimensionValues implements Comparable<DimensionValues> {

    private final String[] values;

    public static final DimensionValues empty = new DimensionValues(new String[] {});

    public static DimensionValues createFrom(String[] values) {
        if (values == null || values.length == 0 || containsAllNulls(values)) return empty;
        return new DimensionValues(values);
    }

    /**
     * Creates a set of dimension values, where the input array <b>must</b> be of
     * the right size, and where no copying is done.
     *
     * @param values the dimension values. This need not be normalized to the right size.
     *               The input array is copied by this.
     */
    private DimensionValues(String[] values) {
        if (values == null) throw new NullPointerException("Dimension values cannot be null");
        this.values = Arrays.copyOf(values, values.length);
    }

    /** Returns true if this is has the same value every place it has a value as the given values. */
    public boolean matches(DimensionValues givenValues) {
        for (int i = 0; i < this.size() || i < givenValues.size() ; i++)
            if ( ! matches(this.get(i), givenValues.get(i)))
                return false;
        return true;
    }

    private final boolean matches(String conditionString, String checkString) {
        if (conditionString == null) return true;
        return conditionString.equals(checkString);
    }

    /**
     * Implements the sort order of this which is based on specificity
     * where dimensions to the left are more significant:
     * -1 is returned if this is more specific than other,
     * 1 is returned if other is more specific than this,
     * 0 is returned if none is more specific than the other.
     * <p>
     * <b>Note:</b> This ordering is not consistent with equals - it returns 0 when the same dimensions
     * are <i>set</i>, regardless of what they are set <i>to</i>.
     */
    @Override
    public int compareTo(DimensionValues other) {
        for (int i=0; i < this.size() || i < other.size(); i++) {
            if (get(i) != null && other.get(i) == null)
                return -1;
            if (get(i) == null && other.get(i) != null)
                return 1;
        }
        return 0;
    }

    /** Helper method which uses compareTo to return whether this is most specific */
    public boolean isMoreSpecificThan(DimensionValues other) {
        return this.compareTo(other) < 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof DimensionValues)) return false;
        DimensionValues other = (DimensionValues) o;
        for (int i = 0; i < this.size() || i < other.size(); i++) {
            if (get(i) == null) {
                if (other.get(i) != null) return false;
            }
            else {
                if ( ! get(i).equals(other.get(i))) return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = 0;
        int i = 0;
        for (String value : values) {
            i++;
            if (value != null)
                hashCode += value.hashCode() * i;
        }
        return hashCode;
    }

    @Override
    public String toString() {
        return "[" + Arrays.stream(values).map(value -> value == null ? "*" : value).collect(Collectors.joining(", ")) + "]";
    }

    public boolean isEmpty() {
        return this==empty;
    }

    private static boolean containsAllNulls(String[] values) {
        for (String value : values)
            if (value != null) return false;
        return true;
    }

    public Map<String,String> asContext(List<String> dimensions) {
        Map<String,String> context = new HashMap<>();
        if (dimensions == null) return context;
        for (int i = 0; i < dimensions.size(); i++)
            context.put(dimensions.get(i), get(i));
        return context;
    }

    /** Returns the string at the given index, or null if it has no value at this index */
    public String get(int index) {
        if (index >= values.length) return null;
        return values[index];
    }

    /** Returns the number of values in this (some of which may be null) */
    public int size() { return values.length; }

    /** Returns a copy of the values in this in an array */
    public String[] getValues() {
        return Arrays.copyOf(values, values.length);
    }

}
