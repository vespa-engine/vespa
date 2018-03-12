// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.yahoo.tensor.functions.Argmax;
import com.yahoo.tensor.functions.Argmin;
import com.yahoo.tensor.functions.Concat;
import com.yahoo.tensor.functions.ConstantTensor;
import com.yahoo.tensor.functions.Diag;
import com.yahoo.tensor.functions.Generate;
import com.yahoo.tensor.functions.Join;
import com.yahoo.tensor.functions.L1Normalize;
import com.yahoo.tensor.functions.L2Normalize;
import com.yahoo.tensor.functions.Matmul;
import com.yahoo.tensor.functions.Random;
import com.yahoo.tensor.functions.Range;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.Rename;
import com.yahoo.tensor.functions.Softmax;
import com.yahoo.tensor.functions.XwPlusB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;

/**
 * A multidimensional array which can be used in computations.
 * <p>
 * A tensor consists of a set of <i>dimension</i> names and a set of <i>cells</i> containing scalar <i>values</i>.
 * Each cell is is identified by its <i>address</i>, which consists of a set of dimension-label pairs which defines
 * the location of that cell. Both dimensions and labels are string on the form of an identifier or integer.
 * <p>
 * The size of the set of dimensions of a tensor is called its <i>order</i>.
 * <p>
 * In contrast to regular mathematical formulations of tensors, this definition of a tensor allows <i>sparseness</i>
 * as there is no built-in notion of a contiguous space, and even in cases where a space is implied (such as when
 * address labels are integers), there is no requirement that every implied cell has a defined value.
 * Undefined values have no define representation as they are never observed.
 * <p>
 * Tensors can be read and serialized to and from a string form documented in the {@link #toString} method.
 *
 * @author bratseth
 */
public interface Tensor {

    // ----------------- Accessors

    TensorType type();

    /** Returns whether this have any cells */
    default boolean isEmpty() { return size() == 0; }

    /** Returns the number of cells in this */
    long size();

    /** Returns the value of a cell, or NaN if this cell does not exist/have no value */
    double get(TensorAddress address);

    /** Returns the cell of this in some undefined order */
    Iterator<Cell> cellIterator();

    /** Returns the values of this in some undefined order */
    Iterator<Double> valueIterator();

    /**
     * Returns an immutable map of the cells of this in no particular order.
     * This may be expensive for some implementations - avoid when possible
     */
    Map<TensorAddress, Double> cells();

    /**
     * Returns the value of this as a double if it has no dimensions and one value
     *
     * @throws IllegalStateException if this does not have zero dimensions and one value
     */
    default double asDouble() {
        if (type().dimensions().size() > 0)
            throw new IllegalStateException("This tensor is not dimensionless. Dimensions: " + type().dimensions().size());
        if (size() == 0) return Double.NaN;
        return valueIterator().next();
    }

    // ----------------- Primitive tensor functions

    default Tensor map(DoubleUnaryOperator mapper) {
        return new com.yahoo.tensor.functions.Map(new ConstantTensor(this), mapper).evaluate();
    }

    /** Aggregates cells over a set of dimensions, or over all dimensions if no dimensions are specified */
    default Tensor reduce(Reduce.Aggregator aggregator, String ... dimensions) {
        return new Reduce(new ConstantTensor(this), aggregator, Arrays.asList(dimensions)).evaluate();
    }
    /** Aggregates cells over a set of dimensions, or over all dimensions if no dimensions are specified */
    default Tensor reduce(Reduce.Aggregator aggregator, List<String> dimensions) {
        return new Reduce(new ConstantTensor(this), aggregator, dimensions).evaluate();
    }

    default Tensor join(Tensor argument, DoubleBinaryOperator combinator) {
        return new Join(new ConstantTensor(this), new ConstantTensor(argument), combinator).evaluate();
    }

    default Tensor rename(String fromDimension, String toDimension) {
        return new Rename(new ConstantTensor(this), Collections.singletonList(fromDimension),
                                                    Collections.singletonList(toDimension)).evaluate();
    }

    default Tensor concat(double argument, String dimension) {
        return concat(Tensor.Builder.of(TensorType.empty).cell(argument).build(), dimension);
    }

    default Tensor concat(Tensor argument, String dimension) {
        return new Concat(new ConstantTensor(this), new ConstantTensor(argument), dimension).evaluate();
    }

    default Tensor rename(List<String> fromDimensions, List<String> toDimensions) {
        return new Rename(new ConstantTensor(this), fromDimensions, toDimensions).evaluate();
    }

    static Tensor generate(TensorType type, Function<List<Long>, Double> valueSupplier) {
        return new Generate(type, valueSupplier).evaluate();
    }

    // ----------------- Composite tensor functions which have a defined primitive mapping

    default Tensor l1Normalize(String dimension) {
        return new L1Normalize(new ConstantTensor(this), dimension).evaluate();
    }

    default Tensor l2Normalize(String dimension) {
        return new L2Normalize(new ConstantTensor(this), dimension).evaluate();
    }

    default Tensor matmul(Tensor argument, String dimension) {
        return new Matmul(new ConstantTensor(this), new ConstantTensor(argument), dimension).evaluate();
    }

    default Tensor softmax(String dimension) {
        return new Softmax(new ConstantTensor(this), dimension).evaluate();
    }

    default Tensor xwPlusB(Tensor w, Tensor b, String dimension) {
        return new XwPlusB(new ConstantTensor(this), new ConstantTensor(w), new ConstantTensor(b), dimension).evaluate();
    }

    default Tensor argmax(String dimension) {
        return new Argmax(new ConstantTensor(this), dimension).evaluate();
    }

    default Tensor argmin(String dimension) { return new Argmin(new ConstantTensor(this), dimension).evaluate(); }

    static Tensor diag(TensorType type) { return new Diag(type).evaluate(); }

    static Tensor random(TensorType type) { return new Random(type).evaluate(); }

    static Tensor range(TensorType type) { return new Range(type).evaluate(); }

    // ----------------- Composite tensor functions mapped to primitives here on the fly

    default Tensor multiply(Tensor argument) { return join(argument, (a, b) -> (a * b )); }
    default Tensor add(Tensor argument) { return join(argument, (a, b) -> (a + b )); }
    default Tensor divide(Tensor argument) { return join(argument, (a, b) -> (a / b )); }
    default Tensor subtract(Tensor argument) { return join(argument, (a, b) -> (a - b )); }
    default Tensor max(Tensor argument) { return join(argument, (a, b) -> (a > b ? a : b )); }
    default Tensor min(Tensor argument) { return join(argument, (a, b) -> (a < b ? a : b )); }
    default Tensor atan2(Tensor argument) { return join(argument, Math::atan2); }
    default Tensor pow(Tensor argument) { return join(argument, Math::pow); }
    default Tensor fmod(Tensor argument) { return join(argument, (a, b) -> ( a % b )); }
    default Tensor ldexp(Tensor argument) { return join(argument, (a, b) -> ( a * Math.pow(2.0, (int)b) )); }
    default Tensor larger(Tensor argument) { return join(argument, (a, b) -> ( a > b ? 1.0 : 0.0)); }
    default Tensor largerOrEqual(Tensor argument) { return join(argument, (a, b) -> ( a >= b ? 1.0 : 0.0)); }
    default Tensor smaller(Tensor argument) { return join(argument, (a, b) -> ( a < b ? 1.0 : 0.0)); }
    default Tensor smallerOrEqual(Tensor argument) { return join(argument, (a, b) -> ( a <= b ? 1.0 : 0.0)); }
    default Tensor equal(Tensor argument) { return join(argument, (a, b) -> ( a == b ? 1.0 : 0.0)); }
    default Tensor notEqual(Tensor argument) { return join(argument, (a, b) -> ( a != b ? 1.0 : 0.0)); }
    default Tensor approxEqual(Tensor argument) { return join(argument, (a, b) -> ( approxEquals(a,b) ? 1.0 : 0.0)); }

    default Tensor avg() { return avg(Collections.emptyList()); }
    default Tensor avg(String dimension) { return avg(Collections.singletonList(dimension)); }
    default Tensor avg(List<String> dimensions) { return reduce(Reduce.Aggregator.avg, dimensions); }
    default Tensor count() { return count(Collections.emptyList()); }
    default Tensor count(String dimension) { return count(Collections.singletonList(dimension)); }
    default Tensor count(List<String> dimensions) { return reduce(Reduce.Aggregator.count, dimensions); }
    default Tensor max() { return max(Collections.emptyList()); }
    default Tensor max(String dimension) { return max(Collections.singletonList(dimension)); }
    default Tensor max(List<String> dimensions) { return reduce(Reduce.Aggregator.max, dimensions); }
    default Tensor min() { return min(Collections.emptyList()); }
    default Tensor min(String dimension) { return min(Collections.singletonList(dimension)); }
    default Tensor min(List<String> dimensions) { return reduce(Reduce.Aggregator.min, dimensions); }
    default Tensor prod() { return prod(Collections.emptyList()); }
    default Tensor prod(String dimension) { return prod(Collections.singletonList(dimension)); }
    default Tensor prod(List<String> dimensions) { return reduce(Reduce.Aggregator.prod, dimensions); }
    default Tensor sum() { return sum(Collections.emptyList()); }
    default Tensor sum(String dimension) { return sum(Collections.singletonList(dimension)); }
    default Tensor sum(List<String> dimensions) { return reduce(Reduce.Aggregator.sum, dimensions); }

    // ----------------- serialization

    /**
     * Returns this tensor on the form
     * <code>{address1:value1,address2:value2,...}</code>
     * where each address is on the form <code>{dimension1:label1,dimension2:label2,...}</code>,
     * and values are numbers.
     * <p>
     * Cells are listed in the natural order of tensor addresses: Increasing size primarily
     * and by element lexical order secondarily.
     * <p>
     * Note that while this is suggestive of JSON, it is not JSON.
     */
    @Override
    String toString();

    /**
     * Call this from toString in implementations to return the standard string format.
     * (toString cannot be a default method because default methods cannot override super methods).
     *
     * @param tensor the tensor to return the standard string format of
     * @return the tensor on the standard string format
     */
    static String toStandardString(Tensor tensor) {
        if (tensor.isEmpty() && ! tensor.type().dimensions().isEmpty()) // explicitly output type TODO: Never do that?
            return tensor.type() + ":" + contentToString(tensor);
        else
            return contentToString(tensor);
    }

    static String contentToString(Tensor tensor) {
        List<java.util.Map.Entry<TensorAddress, Double>> cellEntries = new ArrayList<>(tensor.cells().entrySet());
        if (tensor.type().dimensions().isEmpty()) {
            if (cellEntries.isEmpty()) return "{}";
            return "{" + cellEntries.get(0).getValue() +"}";
        }

        Collections.sort(cellEntries, java.util.Map.Entry.<TensorAddress, Double>comparingByKey());

        StringBuilder b = new StringBuilder("{");
        for (java.util.Map.Entry<TensorAddress, Double> cell : cellEntries) {
            b.append(cell.getKey().toString(tensor.type())).append(":").append(cell.getValue());
            b.append(",");
        }
        if (b.length() > 1)
            b.setLength(b.length() - 1);
        b.append("}");
        return b.toString();
    }

    // ----------------- equality

    /**
     * Returns whether this tensor and the given tensor is mathematically equal:
     * That they have the same dimension *names* and the same content.
     */
    boolean equals(Object o);

    /**
     * Implement here to make this work across implementations.
     * Implementations must override equals and call this because this is an interface and cannot override equals.
     */
    static boolean equals(Tensor a, Tensor b) {
        if (a == b) return true;
        if ( ! a.type().mathematicallyEquals(b.type())) return false;
        if ( a.size() != b.size()) return false;
        for (Iterator<Cell> aIterator = a.cellIterator(); aIterator.hasNext(); ) {
            Cell aCell = aIterator.next();
            double aValue = aCell.getValue();
            double bValue = b.get(aCell.getKey());
            if (!approxEquals(aValue, bValue, 1e-6)) return false;
        }
        return true;
    }

    static boolean approxEquals(double x, double y, double tolerance) {
        return Math.abs(x-y) < tolerance;
    }

    static boolean approxEquals(double x, double y) {
        if (y < -1.0 || y > 1.0) {
            x = Math.nextAfter(x/y, 1.0);
            y = 1.0;
        } else {
            x = Math.nextAfter(x, y);
        }
        return x==y;
    }

    // ----------------- Factories

    /**
     * Returns a tensor instance containing the given data on the standard string format returned by toString
     *
     * @param type the type of the tensor to return
     * @param tensorString the tensor on the standard tensor string format
     */
    static Tensor from(TensorType type, String tensorString) {
        return TensorParser.tensorFrom(tensorString, Optional.of(type));
    }

    /**
     * Returns a tensor instance containing the given data on the standard string format returned by toString
     *
     * @param tensorType the type of the tensor to return, as a string on the tensor type format, given in
     *        {@link TensorType#fromSpec}
     * @param tensorString the tensor on the standard tensor string format
     */
    static Tensor from(String tensorType, String tensorString) {
        return TensorParser.tensorFrom(tensorString, Optional.of(TensorType.fromSpec(tensorType)));
    }

    /**
     * Returns a tensor instance containing the given data on the standard string format returned by toString.
     * If a type is not specified it is derived from the first cell of the tensor
     */
    static Tensor from(String tensorString) {
        return TensorParser.tensorFrom(tensorString, Optional.empty());
    }

    class Cell implements Map.Entry<TensorAddress, Double> {

        private final TensorAddress address;
        private final Double value;

        Cell(TensorAddress address, Double value) {
            this.address = address;
            this.value = value;
        }

        @Override
        public TensorAddress getKey() { return address; }

        /**
         * Returns the direct index which can be used to locate this cell, or -1 if not available.
         * This is for optimizations mapping between tensors where this is possible without creating a
         * TensorAddress.
         */
        long getDirectIndex() { return -1; }

        @Override
        public Double getValue() { return value; }

        @Override
        public Double setValue(Double value) {
            throw new UnsupportedOperationException("A tensor cannot be modified");
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if ( ! ( o instanceof Map.Entry)) return false;
            Map.Entry<?,?> other = (Map.Entry)o;
            if ( ! this.getValue().equals(other.getValue())) return false;
            if ( ! this.getKey().equals(other.getKey())) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return getKey().hashCode() ^ getValue().hashCode(); // by Map.Entry spec
        }

    }

    interface Builder {

        /** Creates a suitable builder for the given type */
        static Builder of(TensorType type) {
            boolean containsIndexed = type.dimensions().stream().anyMatch(d -> d.isIndexed());
            boolean containsMapped = type.dimensions().stream().anyMatch( d ->  ! d.isIndexed());
            if (containsIndexed && containsMapped)
                return MixedTensor.Builder.of(type);
            if (containsMapped)
                return MappedTensor.Builder.of(type);
            else // indexed or empty
                return IndexedTensor.Builder.of(type);
        }

        /** Creates a suitable builder for the given type */
        static Builder of(TensorType type, DimensionSizes dimensionSizes) {
            boolean containsIndexed = type.dimensions().stream().anyMatch(d -> d.isIndexed());
            boolean containsMapped = type.dimensions().stream().anyMatch( d ->  ! d.isIndexed());
            if (containsIndexed && containsMapped)
                return MixedTensor.Builder.of(type);
            if (containsMapped)
                return MappedTensor.Builder.of(type);
            else // indexed or empty
                return IndexedTensor.Builder.of(type, dimensionSizes);
        }

        /** Returns the type this is building */
        TensorType type();

        /** Return a cell builder */
        CellBuilder cell();

        /** Add a cell */
        Builder cell(TensorAddress address, double value);

        /** Add a cell */
        Builder cell(double value, long ... labels);

        /**
         * Add a cell
         *
         * @param cell a cell providing the location at which to add this cell
         * @param value the value to assign to the cell
         */
        default Builder cell(Cell cell, double value) {
            return cell(cell.getKey(), value);
        }

        Tensor build();

        class CellBuilder {

            private final TensorAddress.Builder addressBuilder;
            private final Tensor.Builder tensorBuilder;

            CellBuilder(TensorType type, Tensor.Builder tensorBuilder) {
                addressBuilder = new TensorAddress.Builder(type);
                this.tensorBuilder = tensorBuilder;
            }

            public CellBuilder label(String dimension, String label) {
                addressBuilder.add(dimension, label);
                return this;
            }

            public CellBuilder label(String dimension, long label) {
                return label(dimension, String.valueOf(label));
            }

            public Builder value(double cellValue) {
                return tensorBuilder.cell(addressBuilder.build(), cellValue);
            }

        }

    }

}
