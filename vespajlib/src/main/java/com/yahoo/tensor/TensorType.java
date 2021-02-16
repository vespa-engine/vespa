// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.collect.ImmutableList;
import com.yahoo.text.Ascii7BitMatcher;

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

import static com.yahoo.text.Ascii7BitMatcher.charsAndNumbers;

/**
 * A tensor type with its dimensions. This is immutable.
 * <p>
 * A dimension can be indexed (bound or unbound) or mapped.
 *
 * @author geirst
 * @author bratseth
 */
public class TensorType {

    static Ascii7BitMatcher labelMatcher = new Ascii7BitMatcher("-_@" + charsAndNumbers(), "_@$" + charsAndNumbers());

    /** The permissible cell value types. Default is double. */
    public enum Value {

        // Types added must also be added to TensorTypeParser.parseValueTypeSpec, serialization, and largestOf below
        DOUBLE("double"), FLOAT("float");

        private final String id;

        Value(String id) { this.id = id; }

        public String id() { return id; }

        public boolean isEqualOrLargerThan(TensorType.Value other) {
            return this == other || largestOf(this, other) == this;
        }

        public static Value largestOf(List<Value> values) {
            if (values.isEmpty()) return Value.DOUBLE; // Default
            Value largest = null;
            for (Value value : values) {
                if (largest == null)
                    largest = value;
                else
                    largest = largestOf(largest, value);
            }
            return largest;
        }

        public static Value largestOf(Value value1, Value value2) {
            if (value1 == DOUBLE || value2 == DOUBLE) return DOUBLE;
            return FLOAT;
        }

        @Override
        public String toString() { return name().toLowerCase(); }

        public static Value fromId(String valueTypeString) {
            switch (valueTypeString) {
                case "double" : return Value.DOUBLE;
                case "float" : return Value.FLOAT;
                default : throw new IllegalArgumentException("Value type must be either 'double' or 'float'" +
                                                             " but was '" + valueTypeString + "'");
            }
        }

    };

    /** The empty tensor type - which is the same as a double */
    public static final TensorType empty = new TensorType(Value.DOUBLE, Collections.emptyList());

    private final Value valueType;

    /** Sorted list of the dimensions of this */
    private final ImmutableList<Dimension> dimensions;

    private final TensorType mappedSubtype;

    public TensorType(Value valueType, Collection<Dimension> dimensions) {
        this.valueType = valueType;
        List<Dimension> dimensionList = new ArrayList<>(dimensions);
        Collections.sort(dimensionList);
        this.dimensions = ImmutableList.copyOf(dimensionList);

        if (dimensionList.stream().allMatch(d -> d.isIndexed()))
            mappedSubtype = empty;
        else if (dimensionList.stream().noneMatch(d -> d.isIndexed()))
            mappedSubtype = this;
        else
            mappedSubtype = new TensorType(valueType, dimensions.stream().filter(d -> ! d.isIndexed()).collect(Collectors.toList()));
    }

    static public Value combinedValueType(TensorType ... types) {
        List<Value> valueTypes = new ArrayList<>();
        for (TensorType type : types) {
            if (type.rank() > 0) {
                valueTypes.add(type.valueType());
            }
        }
        return Value.largestOf(valueTypes);
    }

    /**
     * Returns a tensor type instance from a
     * <a href="https://docs.vespa.ai/en/reference/tensor.html#tensor-type-spec">tensor type spec</a>:
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

    /** Returns the numeric type of the cell values of this */
    public Value valueType() { return valueType; }

    /** The type representing the mapped subset of dimensions of this. */
    public TensorType mappedSubtype() { return mappedSubtype; }

    /** Returns the number of dimensions of this: dimensions().size() */
    public int rank() { return dimensions.size(); }

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

    /* Returns the bound of this dimension if it is present and bound in this, empty otherwise */
    public Optional<Long> sizeOfDimension(String dimension) {
        Optional<Dimension> d = dimension(dimension);
        if ( ! d.isPresent()) return Optional.empty();
        return d.get().size();
    }

    /**
     * Returns whether this type can be assigned to the given type,
     * i.e if the given type is a generalization of this type.
     */
    public boolean isAssignableTo(TensorType generalization) {
        return isConvertibleOrAssignableTo(generalization, false, true);
    }

    /**
     * Returns whether this type can be converted to the given type.
     * This is true if this type isAssignableTo the given type or
     * if it is not assignable only because it has a shorter dimension length
     * than the given type in some shared dimension(s), as it can then be
     * converted to the given type by zero padding.
     */
    public boolean isConvertibleTo(TensorType generalization) {
        return isConvertibleOrAssignableTo(generalization, true, true);
    }

    /**
     * Returns whether or not this type can simply be renamed to
     * the given type. This is the same as being assignable, but disregarding
     * dimension names.
     */
    public boolean isRenamableTo(TensorType other) {
        return isConvertibleOrAssignableTo(other, false, false);
    }

    private boolean isConvertibleOrAssignableTo(TensorType generalization, boolean convertible, boolean considerName) {
        if ( ! generalization.valueType().isEqualOrLargerThan(this.valueType) ) return false;
        if (generalization.dimensions().size() != this.dimensions().size()) return false;
        for (int i = 0; i < generalization.dimensions().size(); i++) {
            Dimension thisDimension = this.dimensions().get(i);
            Dimension generalizationDimension = generalization.dimensions().get(i);
            if (thisDimension.isIndexed() != generalizationDimension.isIndexed()) return false;
            if (considerName && ! thisDimension.name().equals(generalizationDimension.name())) return false;
            if (generalizationDimension.size().isPresent()) {
                if ( ! thisDimension.size().isPresent()) return false;
                if (convertible) {
                    if (thisDimension.size().get() > generalizationDimension.size().get()) return false;
                }
                else { // assignable
                    if (!thisDimension.size().get().equals(generalizationDimension.size().get())) return false;
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "tensor" +
               (valueType == Value.DOUBLE ? "" : "<" + valueType.id() + ">") +
               "(" + dimensions.stream().map(Dimension::toString).collect(Collectors.joining(",")) + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TensorType other = (TensorType)o;
        if ( (this.rank() == 0) && (other.rank() == 0)) return true;
        if ( this.valueType != other.valueType) return false;
        if ( ! this.dimensions.equals(other.dimensions)) return false;
        return true;
    }

    /** Returns whether the given type has the same dimension names as this */
    public boolean mathematicallyEquals(TensorType other) {
        if (dimensions().size() != other.dimensions().size()) return false;
        for (int i = 0; i < dimensions().size(); i++)
            if (!dimensions().get(i).name().equals(other.dimensions().get(i).name())) return false;
        return true;
    }

    /**
     * Returns the dimensionwise generalization of this and the given type, or empty if no generalization exists.
     * A dimensionwise generalization exists if the two tensors share the same dimensions, and each dimension
     * is compatible.
     * For example, the dimensionwise generalization of tensor(x[],y[5]) and tensor(x[5],y[]) is tensor(x[],y[])
     */
    public Optional<TensorType> dimensionwiseGeneralizationWith(TensorType other) {
        if (this.equals(other)) return Optional.of(this); // shortcut
        if (this.dimensions.size() != other.dimensions.size()) return Optional.empty();

        Builder b = new Builder(TensorType.Value.largestOf(valueType, other.valueType));
        for (int i = 0; i < dimensions.size(); i++) {
            Dimension thisDim = this.dimensions().get(i);
            Dimension otherDim = other.dimensions().get(i);
            if ( ! thisDim.name().equals(otherDim.name())) return Optional.empty();
            if (thisDim.isIndexed() && otherDim.isIndexed()) {
                if (thisDim.size().isPresent() && otherDim.size().isPresent()) {
                    if ( ! thisDim.size().get().equals(otherDim.size().get()))
                        return Optional.empty();
                    b.dimension(thisDim); // both are equal and bound
                }
                else if (thisDim.size().isPresent()) {
                    b.dimension(otherDim); // use the unbound
                }
                else if (otherDim.size().isPresent()) {
                    b.dimension(thisDim); // use the unbound
                }
                else {
                    b.dimension(thisDim); // both are equal and unbound
                }
            }
            else if ( ! thisDim.isIndexed() && ! otherDim.isIndexed()) {
                b.dimension(thisDim); // both are equal and mapped
            }
            else {
                return Optional.empty(); // one indexed and one mapped
            }
        }
        return Optional.of(b.build());
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimensions, valueType);
    }

    /**
     * A tensor dimension.
     * Dimensions have the natural order of their names.
     */
    public static abstract class Dimension implements Comparable<Dimension> {

        public enum Type { indexedBound, indexedUnbound, mapped }

        private final String name;

        private Dimension(String name) {
            this.name = requireIdentifier(name);
        }

        public final String name() { return name; }

        /** Returns the size of this dimension if it is bound, empty otherwise */
        public abstract Optional<Long> size();

        public abstract Type type();

        /** Returns a copy of this with the name set to the given name */
        public abstract Dimension withName(String name);

        /** Returns true if this is an indexed bound or unbound type */
        public boolean isIndexed() { return type() == Type.indexedBound || type() == Type.indexedUnbound; }

        /** Returns true if this is of the mapped type */
        public boolean isMapped() { return type() == Type.mapped; }

        /**
         * Returns the dimension resulting from combining two dimensions having the same name but possibly different
         * types:
         *
         * [N] + [M] = [ min(N, M) ]
         * [N] + [] = []
         * [] + {} = {}
         */
        Dimension combineWith(Optional<Dimension> other, boolean allowDifferentSizes) {
            if ( ! other.isPresent()) return this;
            if (this instanceof MappedDimension) return this;
            if (other.get() instanceof MappedDimension) return other.get();
            // both are indexed
            if (this instanceof IndexedUnboundDimension) return this;
            if (other.get() instanceof IndexedUnboundDimension) return other.get();
            // both are indexed bound
            IndexedBoundDimension thisIb = (IndexedBoundDimension)this;
            IndexedBoundDimension otherIb = (IndexedBoundDimension)other.get();
            if (allowDifferentSizes)
                return thisIb.size().get() < otherIb.size().get() ? thisIb : otherIb;
            if (  ! thisIb.size().equals(otherIb.size()))
                throw new IllegalArgumentException("Unequal dimension sizes in " + thisIb + " and " + otherIb);
            return thisIb;
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

        public static Dimension indexed(String name, long size) {
            return new IndexedBoundDimension(name, size);
        }

        public static Dimension indexed(String name) {
            return new IndexedUnboundDimension(name);
        }

        public static Dimension mapped(String name) {
            return new MappedDimension(name);
        }

        static private String requireIdentifier(String name) {
            if (name == null)
                throw new IllegalArgumentException("A dimension name cannot be null");
            if ( ! TensorType.labelMatcher.matches(name))
                throw new IllegalArgumentException("A dimension name must be an identifier or integer, not '" + name + "'");
            return name;
        }

    }

    public static class IndexedBoundDimension extends TensorType.Dimension {

        private final Long size;

        private IndexedBoundDimension(String name, long size) {
            super(name);
            if (size < 1)
                throw new IllegalArgumentException("Size of bound dimension '" + name + "' must be at least 1");
            if (size > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Size of bound dimension '" + name + "' cannot be larger than " + Integer.MAX_VALUE);
            this.size = size;
        }

        @Override
        public Optional<Long> size() { return Optional.of(size); }

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
        public Optional<Long> size() { return Optional.empty(); }

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
        public Optional<Long> size() { return Optional.empty(); }

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

        private final Value valueType;

        /** Creates an empty builder with cells of type double */
        public Builder() {
            this(Value.DOUBLE);
        }

        public Builder(Value valueType) {
            this.valueType = valueType;
        }

        /**
         * Creates a builder containing a combination of the dimensions of the given types
         *
         * If the same dimension is indexed with different size restrictions the smallest size will be used.
         * If it is size restricted in one argument but not the other it will not be size restricted.
         * If it is indexed in one and mapped in the other it will become mapped.
         *
         * The value type will be the largest of the value types of the input types
         */
        public Builder(TensorType ... types) {
            this(true, types);
        }

        public Builder(boolean allowDifferentSizes, TensorType ... types) {
            this.valueType = TensorType.combinedValueType(types);
            for (TensorType type : types)
                addDimensionsOf(type, allowDifferentSizes);
        }

        /** Creates a builder from the given dimensions, having double as the value type */
        public Builder(Iterable<Dimension> dimensions) {
            this(Value.DOUBLE, dimensions);
        }

        /** Creates a builder from the given value type and dimensions */
        public Builder(Value valueType, Iterable<Dimension> dimensions) {
            this.valueType = valueType;
            for (TensorType.Dimension dimension : dimensions) {
                dimension(dimension);
            }
        }

        private void addDimensionsOf(TensorType type, boolean allowDifferentSizes) {
            for (Dimension dimension : type.dimensions) {
                set(dimension.combineWith(Optional.ofNullable(dimensions.get(dimension.name())), allowDifferentSizes));
            }
        }

        /** Returns the current number of dimensions in this */
        public int rank() { return dimensions.size(); }

        /**
         * Adds a new dimension to this
         *
         * @throws IllegalArgumentException if the dimension is already present
         */
        private Builder add(Dimension dimension) {
            Objects.requireNonNull(dimension, "A dimension cannot be null");
            if (dimensions.containsKey(dimension.name()))
                throw new IllegalArgumentException("Could not add dimension " + dimension + " as this dimension " +
                                                   "is already present");
            dimensions.put(dimension.name(), dimension);
            return this;
        }

        /** Adds or replaces a dimension in this */
        public Builder set(Dimension dimension) {
            Objects.requireNonNull(dimension, "A dimension cannot be null");
            dimensions.put(dimension.name(), dimension);
            return this;
        }

        /**
         * Adds a bound indexed dimension to this
         *
         * @throws IllegalArgumentException if the dimension is already present
         */
        public Builder indexed(String name, long size) { return add(new IndexedBoundDimension(name, size)); }

        /**
         * Adds an unbound indexed dimension to this
         *
         * @throws IllegalArgumentException if the dimension is already present
         */
        public Builder indexed(String name) {
            return add(new IndexedUnboundDimension(name));
        }

        /**
         * Adds a mapped dimension to this
         *
         * @throws IllegalArgumentException if the dimension is already present
         */
        public Builder mapped(String name) {
            return add(new MappedDimension(name));
        }

        /** Adds the given dimension */
        public Builder dimension(Dimension dimension) {
            return add(dimension);
        }

        /** Returns the given dimension, or empty if none is present */
        public Optional<Dimension> getDimension(String dimension) {
            return Optional.ofNullable(dimensions.get(dimension));
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
            return new TensorType(valueType, dimensions.values());
        }

    }

}

