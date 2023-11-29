// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Denotes which RawFlag should be retrieved from {@link FlagSource} for a given {@link FlagId},
 * as the raw flag may depend on the hostname, application, etc.
 *
 * @author hakonhall
 */
public class FetchVector {

    private final Map<Dimension, String> map;

    public FetchVector() {
        this.map = Map.of();
    }

    public static FetchVector fromMap(Map<Dimension, String> map) {
        return new FetchVector(map);
    }

    private FetchVector(Map<Dimension, String> map) {
        this.map = Map.copyOf(map);
    }

    public Optional<String> getValue(Dimension dimension) {
        return Optional.ofNullable(map.get(dimension));
    }

    public Map<Dimension, String> toMap() { return map; }

    public boolean isEmpty() { return map.isEmpty(); }

    public boolean hasDimension(Dimension dimension) { return map.containsKey(dimension);}

    public Set<Dimension> dimensions() { return map.keySet(); }

    /**
     * Returns a new FetchVector, identical to {@code this} except for its value in {@code dimension}.
     * Dimension is removed if the value is null.
     */
    public FetchVector with(Dimension dimension, String value) {
        if (value == null) return makeFetchVector(merged -> merged.remove(dimension));
        return makeFetchVector(merged -> merged.put(dimension, value));
    }

    /** Returns a new FetchVector, identical to {@code this} except for its values in the override's dimensions. */
    public FetchVector with(FetchVector override) {
        return makeFetchVector(vector -> vector.putAll(override.map));
    }

    private FetchVector makeFetchVector(Consumer<Map<Dimension, String>> mapModifier) {
        Map<Dimension, String> mergedMap = new EnumMap<>(Dimension.class);
        mergedMap.putAll(map);
        mapModifier.accept(mergedMap);
        return new FetchVector(mergedMap);
    }

    public FetchVector without(Dimension dimension) {
        return makeFetchVector(merged -> merged.remove(dimension));
    }

    public FetchVector without(Collection<Dimension> dimensions) {
        return makeFetchVector(merged -> merged.keySet().removeAll(dimensions));
    }

    @Override
    public String toString() {
        return "FetchVector{" +
               "map=" + map +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FetchVector that = (FetchVector) o;
        return Objects.equals(map, that.map);
    }

    @Override
    public int hashCode() {
        return Objects.hash(map);
    }
}
