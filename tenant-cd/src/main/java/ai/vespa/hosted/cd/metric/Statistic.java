// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd.metric;

import java.util.HashMap;
import java.util.List;
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
        this.data = data;
    }

    public static Statistic of(Map<Type, Double> data) {
        for (Type type : List.of(Type.count, Type.rate, Type.average))
            if ( ! data.containsKey(type))
                throw new IllegalArgumentException("Required data type '" + type + "' not present in '" + data + "'");

        return new Statistic(copyOf(data));
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

    Statistic mergedWith(Statistic other) {
        if (data.keySet().equals(other.data.keySet()))
            throw new IllegalArgumentException("Unequal key sets '" + data.keySet() + "' and '" + other.data.keySet() + "'.");

        Map<Type, Double> merged = new HashMap<>();
        double n1 = get(Type.count), n2 = other.get(Type.count);
        for (Type type : data.keySet()) switch (type) {
            case count: merged.put(type, n1 + n2); break;
            case rate: merged.put(type, get(Type.rate) + other.get(Type.rate)); break;
            case max: merged.put(type, Math.max(get(Type.max), other.get(Type.max))); break;
            case min: merged.put(type, Math.min(get(Type.min), other.get(Type.min))); break;
            case average: merged.put(type, (n1 * get(Type.average) + n2 * other.get(Type.average)) / (n1 + n2)); break;
            case last:
            case percentile95:
            case percentile99: break;
            default: throw new IllegalArgumentException("Unexpected type '" + type + "'.");
        }
        return of(merged);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Statistic.class.getSimpleName() + "[", "]")
                .add("data=" + data)
                .toString();
    }

}
