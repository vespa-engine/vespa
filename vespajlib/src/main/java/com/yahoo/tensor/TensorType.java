// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A tensor type with its dimensions. This is immutable.
 * <p>
 * A dimension can be indexed (bound or unbound) or mapped.
 * Currently, we only support tensor types where all dimensions have the same type.
 *
 * @author geirst
 * @author bratseth
 */
@Beta
public class TensorType {

    public static final TensorType empty = new TensorType(Collections.emptyList());

    private final ImmutableList<Dimension> dimensions;

    private TensorType(Collection<Dimension> dimensions) {
        this.dimensions = ImmutableList.copyOf(dimensions);
    }

    /**
     * Returns a tensor type instance from a string on the format
     * <code>tensor(dimension1, dimension2, ...)</code>
     * where each dimension is either
     * <ul>
     *     <li><code>dimension-name[]</code> - an unbound indexed dimension
     *     <li><code>dimension-name[int]</code> - an bound indexed dimension
     *     <li><code>dimension-name{}</code> - a mapped dimension
     * </ul>
     * Example: <code>tensor(x[10],y[20])</code> (a matrix)
     */
    public static TensorType fromSpec(String specString) {
        return TensorTypeParser.fromSpec(specString);
    }

    /** 
     * Returns a new tensor type which is the combination of the dimensions of both arguments.
     * If the same dimension is indexed with different size restrictions the largest size will be used.
     * If it is size restricted in one argument but not the other it will not be size restricted.
     * If it is indexed in one and mapped in the other it will become mapped.
     */
    public TensorType combineWith(TensorType other) {
        TensorType.Builder b = new TensorType.Builder();
        for (Dimension thisDimension : dimensions)
            b.add(thisDimension);
        for (Dimension otherDimension : other.dimensions) {
            Dimension thisDimension = b.dimensions.get(otherDimension.name());
            b.addOrReplace(otherDimension.combineWith(Optional.ofNullable(thisDimension)));
        }
        return b.build();
    }
    
    /** Returns an immutable list of the dimensions of this */
    public List<Dimension> dimensions() { return dimensions; }
    
    /** Returns an immutable set of the names of the dimensions of this */
    public Set<String> dimensionNames() {
        return dimensions.stream().map(Dimension::name).collect(Collectors.toSet());
    }

    @Override
    public String toString() {
        return "tensor(" + dimensions.stream().map(Dimension::toString).collect(Collectors.joining(",")) + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        return new HashSet<>(dimensions).equals(new HashSet<>(((TensorType)other).dimensions));
    }

    @Override
    public int hashCode() {
        return dimensions.hashCode();
    }

    /**
     * A tensor dimension.
     * Dimensions have the natural order of their names.
     */
    public static abstract class Dimension implements Comparable<Dimension> {

        public enum Type { indexedBound, indexedUnbound, mapped }

        private final String name;

        private Dimension(String name) { 
            Objects.requireNonNull(name, "A tensor name cannot be null");
            this.name = name; 
        }

        public final String name() { return name; }

        /** Returns the size of this dimension if it is indexedUnbound, empty otherwise */
        public abstract Optional<Integer> size();

        public abstract Type type();

        Dimension combineWith(Optional<Dimension> other) {
            if ( ! other.isPresent()) return this;
            if (this instanceof MappedDimension) return this;
            if (other.get() instanceof MappedDimension) return other.get();
            // both are indexed
            if (this instanceof IndexedUnboundDimension) return this;
            if (other.get() instanceof IndexedUnboundDimension) return other.get();
            // both are indexed bound
            IndexedBoundDimension thisIb = (IndexedBoundDimension)this;
            IndexedBoundDimension otherIb = (IndexedBoundDimension)other.get();
            return thisIb.size().get() > otherIb.size().get() ? thisIb : otherIb;
        }
        
        @Override
        public abstract String toString();

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            return name.equals(((Dimension)other).name);
        }
        
        @Override
        public int hashCode() {
            return name.hashCode();
        }
        
        @Override
        public int compareTo(Dimension other) {
            return this.name.compareTo(other.name);
        }
        
    }

    public static class IndexedBoundDimension extends TensorType.Dimension {

        private final Optional<Integer> size;

        private IndexedBoundDimension(String name, int size) {
            super(name);
            if (size < 1)
                throw new IllegalArgumentException("Size of bound dimension '" + name + "' must be at least 1");
            this.size = Optional.of(size);
        }

        @Override
        public Optional<Integer> size() { return size; }

        @Override
        public Type type() { return Type.indexedBound; }

        @Override
        public String toString() { return name() + "[" + size.get() + "]"; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            IndexedBoundDimension that = (IndexedBoundDimension) o;

            if (!size.equals(that.size)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + size.hashCode();
            return result;
        }
    }

    public static class IndexedUnboundDimension extends TensorType.Dimension {

        private IndexedUnboundDimension(String name) {
            super(name);
        }

        @Override
        public Optional<Integer> size() { return Optional.empty(); }

        @Override
        public Type type() { return Type.indexedUnbound; }

        @Override
        public String toString() { return name() + "[]"; }
    }

    public static class MappedDimension extends TensorType.Dimension {

        private MappedDimension(String name) {
            super(name);
        }

        @Override
        public Optional<Integer> size() { return Optional.empty(); }

        @Override
        public Type type() { return Type.mapped; }

        @Override
        public String toString() { return name() + "{}"; }

    }

    public static class Builder {

        private final Map<String, Dimension> dimensions = new LinkedHashMap<>();
        private Dimension prevDimension = null;

        private Builder add(Dimension dimension) {
            Objects.requireNonNull(dimension, "A dimension cannot be null");

            if ( ! dimensions.isEmpty()) {
                validateDimensionName(dimension);
                validateDimensionType(dimension);
            }

            dimensions.put(dimension.name(), dimension);
            prevDimension = dimension;
            return this;
        }

        private Builder addOrReplace(Dimension dimension) { // TODO: Not quite sure I like this solution
            Objects.requireNonNull(dimension, "A dimension cannot be null");

            if ( ! dimensions.isEmpty()) {
                validateDimensionType(dimension);
            }

            dimensions.put(dimension.name(), dimension);
            prevDimension = dimension;
            return this;
        }

        private void validateDimensionName(Dimension newDimension) {
            Dimension prevDimension = dimensions.get(newDimension.name());
            if (prevDimension != null) {
                throw new IllegalArgumentException("Expected all dimensions to have unique names, " +
                                                   "but '" + prevDimension + "' and '" + newDimension + 
                                                   "' have the same name");
            }
        }

        private void validateDimensionType(Dimension newDimension) {
            if (prevDimension.type() != newDimension.type()) {
                throw new IllegalArgumentException("Expected all dimensions to have the same type, " +
                                                   "but '" + prevDimension + "' does not have the same type as '" + 
                                                   newDimension + "'");
            }
        }

        public Builder indexedBound(String name, int size) {
            return add(new IndexedBoundDimension(name, size));
        }

        public Builder indexedUnbound(String name) {
            return add(new IndexedUnboundDimension(name));
        }

        public Builder mapped(String name) {
            return add(new MappedDimension(name));
        }

        public TensorType build() {
            return new TensorType(dimensions.values());
        }

    }

}

