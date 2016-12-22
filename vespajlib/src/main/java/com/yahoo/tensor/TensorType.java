// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

    /** Sorted list of the dimensions of this */
    private final ImmutableList<Dimension> dimensions;

    private TensorType(Collection<Dimension> dimensions) {
        List<Dimension> dimensionList = new ArrayList<>(dimensions);
        Collections.sort(dimensionList);
        this.dimensions = ImmutableList.copyOf(dimensionList);
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

    /** Returns true if all dimensions of this are indexed */
    public boolean isIndexed() { return dimensions().stream().allMatch(d -> d.isIndexed()); }
    
    private static final boolean supportsMixedTypes = false;
    
    /** 
     * Returns a new tensor type which is the combination of the dimensions of both arguments.
     * If the same dimension is indexed with different size restrictions the largest size will be used.
     * If it is size restricted in one argument but not the other it will not be size restricted.
     * If it is indexed in one and mapped in the other it will become mapped.
     */
    public TensorType combineWith(TensorType other) {
        if ( ! supportsMixedTypes) return combineWithAndDisallowMixedTypes(other); // TODO: Support it
        
        if (this.equals(other)) return this;

        TensorType.Builder b = new TensorType.Builder();
        for (Dimension thisDimension : dimensions)
            b.add(thisDimension);
        for (Dimension otherDimension : other.dimensions) {
            Dimension thisDimension = b.dimensions.get(otherDimension.name());
            b.set(otherDimension.combineWith(Optional.ofNullable(thisDimension)));
        }
        return b.build();
    }

    private TensorType combineWithAndDisallowMixedTypes(TensorType other) {
        if (this.equals(other)) return this;
        
        boolean containsMapped = dimensions().stream().anyMatch(d -> ! d.isIndexed());
        containsMapped = containsMapped || other.dimensions().stream().anyMatch(d -> ! d.isIndexed());

        TensorType.Builder b = new TensorType.Builder();
        for (Dimension thisDimension : dimensions) {
            if (containsMapped)
                thisDimension = new MappedDimension(thisDimension.name());
            b.add(thisDimension);
        }
        for (Dimension otherDimension : other.dimensions) {
            if (containsMapped)
                otherDimension = new MappedDimension(otherDimension.name());
            Dimension thisDimension = b.dimensions.get(otherDimension.name());
            b.set(otherDimension.combineWith(Optional.ofNullable(thisDimension)));
        }
        return b.build();
    }
    
    
    /** Returns an immutable list of the dimensions of this */
    public List<Dimension> dimensions() { return dimensions; }
    
    /** Returns an immutable set of the names of the dimensions of this */
    public Set<String> dimensionNames() {
        return dimensions.stream().map(Dimension::name).collect(Collectors.toSet());
    }
    
    /** Returns the dimension with this name, or empty if not present */
    public Optional<Dimension> dimension(String name) {
        return indexOfDimension(name).map(i -> dimensions.get(i));
    }

    /** Returns the 0-base index of this dimension, or empty if it is not present */
    public Optional<Integer> indexOfDimension(String dimension) {
        for (int i = 0; i < dimensions.size(); i++)
            if (dimensions.get(i).name().equals(dimension))
                return Optional.of(i);
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "tensor(" + dimensions.stream().map(Dimension::toString).collect(Collectors.joining(",")) + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        return dimensions.equals(((TensorType)other).dimensions);
    }

    /** Returns whether the given type has the same dimension names as this */
    public boolean mathematicallyEquals(TensorType other) {
        if (dimensions().size() != other.dimensions().size()) return false;
        for (int i = 0; i < dimensions().size(); i++)
            if (!dimensions().get(i).name().equals(other.dimensions().get(i).name())) return false;
        return true;
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

        /** Returns the size of this dimension if it is bound, empty otherwise */
        public abstract Optional<Integer> size();

        public abstract Type type();

        /** Returns a copy of this with the name set to the given name */
        public abstract Dimension withName(String name);

        /** Returns true if this is an indexed bound or unboun type */
        public boolean isIndexed() { return type() == Type.indexedBound || type() == Type.indexedUnbound; }

        /** 
         * Returns the dimension resulting from combining two dimensions having the same name but possibly different
         * types. This works by degrading to the type making the fewer promises.
         * [N] + [M] = [min(N, M)]
         * [N] + [] = []
         * [] + {} = {}
         */
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
            return thisIb.size().get() < otherIb.size().get() ? thisIb : otherIb;
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

        private final Integer size;

        private IndexedBoundDimension(String name, int size) {
            super(name);
            if (size < 1)
                throw new IllegalArgumentException("Size of bound dimension '" + name + "' must be at least 1");
            this.size = size;
        }

        @Override
        public Optional<Integer> size() { return Optional.of(size); }

        @Override
        public Type type() { return Type.indexedBound; }

        @Override
        public IndexedBoundDimension withName(String name) {
            return new IndexedBoundDimension(name, size);
        }

        @Override
        public String toString() { return name() + "[" + size + "]"; }

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
        public IndexedUnboundDimension withName(String name) {
            return new IndexedUnboundDimension(name);
        }

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
        public MappedDimension withName(String name) {
            return new MappedDimension(name);
        }

        @Override
        public String toString() { return name() + "{}"; }

    }

    public static class Builder {

        private final Map<String, Dimension> dimensions = new LinkedHashMap<>();

        /** Add a new dimension */
        private Builder add(Dimension dimension) {
            Objects.requireNonNull(dimension, "A dimension cannot be null");
            if (dimensions.containsKey(dimension.name()))
                throw new IllegalArgumentException("Could not add dimension " + dimension + " as this dimension " +
                                                   "is already present");
            dimensions.put(dimension.name(), dimension);
            return this;
        }

        /** Add or replace a dimension */
        private Builder set(Dimension dimension) {
            Objects.requireNonNull(dimension, "A dimension cannot be null");
            dimensions.put(dimension.name(), dimension);
            return this;
        }

        /** Create a bound indexed dimension */
        public Builder indexed(String name, int size) { return add(new IndexedBoundDimension(name, size)); }

        /** Create an unbound indexed dimension */
        public Builder indexed(String name) {
            return add(new IndexedUnboundDimension(name));
        }

        public Builder mapped(String name) {
            return add(new MappedDimension(name));
        }

        public Builder dimension(Dimension dimension) {
            return add(dimension);
        }

        public Builder dimension(String name, Dimension.Type type) {
            switch (type) {
                case mapped : mapped(name); break;
                case indexedUnbound : indexed(name); break;
                default : throw new IllegalArgumentException("This can not create a dimension of type " + type);
            }
            return this;
        }

        public TensorType build() {
            return new TensorType(dimensions.values());
        }
        
    }

}

