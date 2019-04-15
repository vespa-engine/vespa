package com.yahoo.vespa.tenant.cd.metrics;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringJoiner;

import static java.util.Map.copyOf;

/**
 * Known statistic about a metric, at a certain point.
 *
 * @author jonmv
 */
public class Statistic {

    private final Map<Type, Double> data;

    /** Creates a new Statistic with a copy of the given data. */
    private Statistic(Map<Type, Double> data) {
        this.data = copyOf(data);
    }

    public static Statistic of(Map<Type, Double> data) {
        if (data.containsKey(null) || data.containsValue(null))
            throw new IllegalArgumentException("Data may not contain null keys or values: '" + data + "'.");

        return new Statistic(data);
    }

    /** Returns the value of the given type, or throws a NoSuchElementException if this isn't known. */
    public double get(Type key) {
        if ( ! data.containsKey(key))
            throw new NoSuchElementException("No value with key '" + key + "' is known.");

        return data.get(key);
    }

    /** Returns the underlying, unmodifiable Map. */
    public Map<Type, Double> asMap() {
        return data;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Statistic.class.getSimpleName() + "[", "]")
                .add("data=" + data)
                .toString();
    }

}
