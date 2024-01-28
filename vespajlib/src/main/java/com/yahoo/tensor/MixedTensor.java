// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A mixed tensor type. This is class is currently suitable for serialization
 * and deserialization, not yet for computation.
 *
 * A mixed tensor has a combination of mapped and indexed dimensions. By
 * reordering the mapped dimensions before the indexed dimensions, one can
 * think of mixed tensors as the mapped dimensions mapping to a
 * dense tensor. This dense tensor is called a dense subspace.
 *
 * @author lesters
 */
public class MixedTensor implements Tensor {

    /** The dimension specification for this tensor */
    private final TensorType type;

    // XXX consider using "record" instead
    /** only exposed for internal use; subject to change without notice */
    public static final class DenseSubspace {
        public final TensorAddress sparseAddress;
        public final double[] cells;
        DenseSubspace(TensorAddress sparseAddress, double[] cells) {
            this.sparseAddress = sparseAddress;
            this.cells = cells;
        }
        @Override public int hashCode() {
            return Objects.hash(sparseAddress, cells[0]);
        }
        @Override public boolean equals(Object other) {
            if (other instanceof DenseSubspace o) {
                return sparseAddress.equals(o.sparseAddress) && Arrays.equals(cells, o.cells);
            }
            return false;
        }
    }

    /** only exposed for internal use; subject to change without notice */
    public List<DenseSubspace> getInternalDenseSubspaces() { return index.denseSubspaces; }

    /** An index structure over the cell list */
    private final Index index;

    private MixedTensor(TensorType type, Index index) {
        this.type = type;
        this.index = index;
    }

    /** Returns the tensor type */
    @Override
    public TensorType type() { return type; }

    /** Returns the size of the tensor measured in number of cells */
    @Override
    public long size() { return index.denseSubspaces.size() * index.denseSubspaceSize; }

    /** Returns the value at the given address */
    @Override
    public double get(TensorAddress address) {
        var block = index.blockOf(address);
        int denseOffset = index.denseOffsetOf(address);
        if (block == null || denseOffset < 0 || denseOffset >= block.cells.length) {
            return 0.0;
        }
        return block.cells[denseOffset];
    }

    @Override
    public Double getAsDouble(TensorAddress address) {
        var block = index.blockOf(address);
        int denseOffset = index.denseOffsetOf(address);
        if (block == null || denseOffset < 0 || denseOffset >= block.cells.length) {
            return null;
        }
        return block.cells[denseOffset];
    }

    @Override
    public boolean has(TensorAddress address) {
        var block = index.blockOf(address);
        int denseOffset = index.denseOffsetOf(address);
        return (block != null && denseOffset >= 0 && denseOffset < block.cells.length);
    }

    /**
     * Returns an iterator over the cells of this tensor.
     * Cells are returned in order of increasing indexes in the
     * indexed dimensions, increasing indexes of later dimensions
     * in the dimension type before earlier. No guarantee is
     * given for the order of sparse dimensions.
     */
    @Override
    public Iterator<Cell> cellIterator() {
        return new Iterator<>() {

            final Iterator<DenseSubspace> blockIterator = index.denseSubspaces.iterator();
            final int[] labels = new int[index.indexedDimensions.size()];
            DenseSubspace currentBlock = null;
            int currOffset = index.denseSubspaceSize;
            int prevOffset = -1;

            @Override
            public boolean hasNext() {
                return (currOffset < index.denseSubspaceSize || blockIterator.hasNext());
            }

            @Override
            public Cell next() {
                if (currOffset == index.denseSubspaceSize) {
                    currentBlock = blockIterator.next();
                    currOffset = 0;
                }
                if (currOffset != prevOffset) { // Optimization for index.denseSubspaceSize == 1
                    index.denseOffsetToAddress(currOffset, labels);
                }
                TensorAddress fullAddr = currentBlock.sparseAddress.fullAddressOf(index.type.dimensions(), labels);
                prevOffset = currOffset;
                double value = currentBlock.cells[currOffset++];
                return new Cell(fullAddr, value);
            }
        };
    }

    /**
     * Returns an iterator over the values of this tensor.
     * The iteration order is the same as for cellIterator.
     */
    @Override
    public Iterator<Double> valueIterator() {
        return new Iterator<>() {

            final Iterator<DenseSubspace> blockIterator = index.denseSubspaces.iterator();
            double[] currentBlock = null;
            int currOffset = index.denseSubspaceSize;

            @Override
            public boolean hasNext() {
                return (currOffset < index.denseSubspaceSize || blockIterator.hasNext());
            }

            @Override
            public Double next() {
                if (currOffset == index.denseSubspaceSize) {
                    currentBlock = blockIterator.next().cells;
                    currOffset = 0;
                }
                return currentBlock[currOffset++];
            }
        };
    }

    @Override
    public Map<TensorAddress, Double> cells() {
        ImmutableMap.Builder<TensorAddress, Double> builder = new ImmutableMap.Builder<>();
        var iter = cellIterator();
        while (iter.hasNext()) {
            Cell cell = iter.next();
            builder.put(cell.getKey(), cell.getValue());
        }
        return builder.build();
    }

    @Override
    public Tensor withType(TensorType other) {
        if (!this.type.isRenamableTo(type)) {
            throw new IllegalArgumentException("MixedTensor.withType: types are not compatible. Current type: '" +
                                               this.type + "', requested type: '" + type + "'");
        }
        return new MixedTensor(other, index);
    }

    @Override
    public Tensor remove(Set<TensorAddress> addresses) {
        var indexBuilder = new Index.Builder(type);
        for (var block : index.denseSubspaces) {
            if ( ! addresses.contains(block.sparseAddress)) {  // assumption: addresses only contain the sparse part
                indexBuilder.addBlock(block);
            }
        }
        return new MixedTensor(type, indexBuilder.build());
    }

    @Override
    public int hashCode() { return Objects.hash(type, index.denseSubspaces); }

    @Override
    public String toString() {
        return toString(true, true);
    }

    @Override
    public String toString(boolean withType, boolean shortForms) {
        return toString(withType, shortForms, Long.MAX_VALUE);
    }

    @Override
    public String toAbbreviatedString(boolean withType, boolean shortForms) {
        return toString(withType, shortForms, Math.max(2, 10 / (type().dimensions().stream().filter(TensorType.Dimension::isMapped).count() + 1)));
    }

    private String toString(boolean withType, boolean shortForms, long maxCells) {
        if (! shortForms
            || type.rank() == 0
            || type.rank() > 1 && type.dimensions().stream().filter(TensorType.Dimension::isIndexed).anyMatch(d -> d.size().isEmpty())
            || type.dimensions().stream().filter(TensorType.Dimension::isMapped).count() > 1)
            return Tensor.toStandardString(this, withType, shortForms, maxCells);

        return (withType ? type + ":" : "") + index.contentToString(this, maxCells);
    }

    @Override
    public boolean equals(Object other) {
        if ( ! ( other instanceof Tensor)) return false;
        return Tensor.equals(this, ((Tensor)other));
    }

    /** Returns the size of dense subspaces */
    public long denseSubspaceSize() {
        return index.denseSubspaceSize;
    }

    /**
     * Base class for building mixed tensors.
     */
    public abstract static class Builder implements Tensor.Builder {
        static final int INITIAL_HASH_CAPACITY = 1000;

        final TensorType type;

        /**
         * Create a builder depending upon the type of indexed dimensions.
         * If at least one indexed dimension is unbound, we create
         * a temporary structure while finding dimension bounds.
         */
        public static Builder of(TensorType type) {
            //TODO Wire in expected map size to avoid expensive resize
            if (type.hasIndexedUnboundDimensions()) {
                return new UnboundBuilder(type, INITIAL_HASH_CAPACITY);
            } else {
                return new BoundBuilder(type, INITIAL_HASH_CAPACITY);
            }
        }

        private Builder(TensorType type) {
            this.type = type;
        }

        @Override
        public TensorType type() {
            return type;
        }

        @Override
        public Tensor.Builder cell(float value, long... labels) {
            return cell((double)value, labels);
        }

        @Override
        public Tensor.Builder cell(double value, long... labels) {
            throw new UnsupportedOperationException("Not implemented.");
        }

        @Override
        public CellBuilder cell() {
            return new CellBuilder(type(), this);
        }

        @Override
        public abstract MixedTensor build();
    }

    /**
     * Builder for mixed tensors with bound indexed dimensions.
     */
    public static class BoundBuilder extends Builder {

        /** For each sparse partial address, hold a dense subspace */
        private final Map<TensorAddress, double[]> denseSubspaceMap;
        private final Index.Builder indexBuilder;
        private final Index index;
        private final TensorType denseSubtype;

        private BoundBuilder(TensorType type, int expectedSize) {
            super(type);
            denseSubspaceMap = new LinkedHashMap<>(expectedSize, 0.5f);
            indexBuilder = new Index.Builder(type);
            index = indexBuilder.index();
            denseSubtype = new TensorType(type.valueType(),
                                          type.dimensions().stream().filter(TensorType.Dimension::isIndexed).toList());
        }

        public long denseSubspaceSize() {
            return index.denseSubspaceSize();
        }

        private double[] denseSubspace(TensorAddress sparseAddress) {
            return denseSubspaceMap.computeIfAbsent(sparseAddress, (key) -> new double[(int)denseSubspaceSize()]);
        }

        public IndexedTensor.DirectIndexBuilder denseSubspaceBuilder(TensorAddress sparseAddress) {
            double[] values = new double[(int)denseSubspaceSize()];
            denseSubspaceMap.put(sparseAddress, values);
            return new DenseSubspaceBuilder(denseSubtype, values);
        }

        @Override
        public Tensor.Builder cell(TensorAddress address, float value) {
            return cell(address, (double)value);
        }

        @Override
        public Tensor.Builder cell(TensorAddress address, double value) {
            TensorAddress sparsePart = address.mappedPartialAddress(index.sparseType, index.type.dimensions());
            int denseOffset = index.denseOffsetOf(address);
            double[] denseSubspace = denseSubspace(sparsePart);
            denseSubspace[denseOffset] = value;
            return this;
        }

        public Tensor.Builder block(TensorAddress sparsePart, double[] values) {
            int denseSubspaceSize = (int)denseSubspaceSize();
            if (values.length < denseSubspaceSize)
                throw new IllegalArgumentException("Block should have " + denseSubspaceSize +
                                                   " values, but has only " + values.length);
            double[] denseSubspace = denseSubspace(sparsePart);
            System.arraycopy(values, 0, denseSubspace, 0, denseSubspaceSize);
            return this;
        }

        @Override
        public MixedTensor build() {
            //TODO This can be solved more efficiently with a single map.
            Set<Map.Entry<TensorAddress, double[]>> entrySet = denseSubspaceMap.entrySet();
            for (Map.Entry<TensorAddress, double[]> entry : entrySet) {
                TensorAddress sparsePart = entry.getKey();
                double[] denseSubspace = entry.getValue();
                var block = new DenseSubspace(sparsePart, denseSubspace);
                indexBuilder.addBlock(block);
            }
            return new MixedTensor(type, indexBuilder.build());
        }

        public static BoundBuilder of(TensorType type) {
            //TODO Wire in expected map size to avoid expensive resize
            return new BoundBuilder(type, INITIAL_HASH_CAPACITY);
        }
    }

    /**
     * Temporarily stores all cells to find bounds of indexed dimensions,
     * then creates a tensor using BoundBuilder. This is due to the
     * fact that for serialization the size of the dense subspace must be
     * known, and equal for all dense subspaces. A side effect is that the
     * tensor type is effectively changed, such that unbound indexed
     * dimensions become bound.
     */
    private static class UnboundBuilder extends Builder {

        private final Map<TensorAddress, Double> cells;
        private final long[] dimensionBounds;

        private UnboundBuilder(TensorType type, int expectedSize) {
            super(type);
            cells = new LinkedHashMap<>(expectedSize, 0.5f);
            dimensionBounds = new long[type.dimensions().size()];
        }

        @Override
        public Tensor.Builder cell(TensorAddress address, float value) {
            return cell(address, (double)value);
        }

        @Override
        public Tensor.Builder cell(TensorAddress address, double value) {
            cells.put(address, value);
            trackBounds(address);
            return this;
        }

        @Override
        public MixedTensor build() {
            TensorType boundType = createBoundType();
            BoundBuilder builder = new BoundBuilder(boundType, cells.size());
            for (Map.Entry<TensorAddress, Double> cell : cells.entrySet()) {
                builder.cell(cell.getKey(), cell.getValue());
            }
            return builder.build();
        }

        private void trackBounds(TensorAddress address) {
            for (int i = 0; i < type.dimensions().size(); ++i) {
                TensorType.Dimension dimension = type.dimensions().get(i);
                if (dimension.isIndexed()) {
                    dimensionBounds[i] = Math.max(address.numericLabel(i), dimensionBounds[i]);
                }
            }
        }

        private TensorType createBoundType() {
            TensorType.Builder typeBuilder = new TensorType.Builder(type().valueType());
            for (int i = 0; i < type.dimensions().size(); ++i) {
                TensorType.Dimension dimension = type.dimensions().get(i);
                if (!dimension.isIndexed()) {
                    typeBuilder.mapped(dimension.name());
                } else {
                    long size = dimension.size().orElse(dimensionBounds[i] + 1);
                    typeBuilder.indexed(dimension.name(), size);
                }
            }
            return typeBuilder.build();
        }

        public static UnboundBuilder of(TensorType type) {
            //TODO Wire in expected map size to avoid expensive resize
            return new UnboundBuilder(type, INITIAL_HASH_CAPACITY);
        }
    }

    /**
     * An immutable index into a list of cells.
     * Contains additional information required
     * for handling mixed tensor addresses.
     * Assumes indexed dimensions are bound.
     */
    private static class Index {

        private final TensorType type;
        private final TensorType sparseType;
        private final TensorType denseType;
        private final List<TensorType.Dimension> mappedDimensions;
        private final List<TensorType.Dimension> indexedDimensions;
        private final int[] indexedDimensionsSize;

        private ImmutableMap<TensorAddress, Integer> sparseMap;
        private List<DenseSubspace> denseSubspaces;
        private final int denseSubspaceSize;

        static private int computeDSS(List<TensorType.Dimension> dimensions) {
            long denseSubspaceSize = 1;
            for (var dimension : dimensions) {
                denseSubspaceSize *= dimension.size()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown size of indexed dimension"));
            }
            return (int) denseSubspaceSize;
        }

        private Index(TensorType type) {
            this.type = type;
            this.mappedDimensions = type.dimensions().stream().filter(d -> !d.isIndexed()).toList();
            this.indexedDimensions = type.dimensions().stream().filter(TensorType.Dimension::isIndexed).toList();
            this.indexedDimensionsSize = new int[indexedDimensions.size()];
            for (int i = 0; i < indexedDimensions.size(); i++) {
                long dimensionSize = indexedDimensions.get(i).size().orElseThrow(() ->
                        new IllegalArgumentException("Unknown size of indexed dimension."));
                indexedDimensionsSize[i] = (int)dimensionSize;
            }

            this.sparseType = createPartialType(type.valueType(), mappedDimensions);
            this.denseType = createPartialType(type.valueType(), indexedDimensions);
            this.denseSubspaceSize = computeDSS(this.indexedDimensions);
            if (this.denseSubspaceSize < 1) {
                throw new IllegalStateException("invalid dense subspace size: " + denseSubspaceSize);
            }
        }

        private DenseSubspace blockOf(TensorAddress address) {
            TensorAddress sparsePart = address.mappedPartialAddress(sparseType, type.dimensions());
            Integer blockNum = sparseMap.get(sparsePart);
            if (blockNum == null || blockNum >= denseSubspaces.size()) {
                return null;
            }
            return denseSubspaces.get(blockNum);
        }

        private int denseOffsetOf(TensorAddress address) {
            long innerSize = 1;
            long offset = 0;
            for (int i = type.dimensions().size(); --i >= 0; ) {
                TensorType.Dimension dimension = type.dimensions().get(i);
                if (dimension.isIndexed()) {
                    long label = address.numericLabel(i);
                    offset += label * innerSize;
                    innerSize *= dimension.size().orElseThrow(() ->
                            new IllegalArgumentException("Unknown size of indexed dimension."));
                }
            }
            return (int) offset;
        }

        public int denseSubspaceSize() {
            return denseSubspaceSize;
        }

        private void denseOffsetToAddress(long denseOffset, int [] labels) {
            if (denseOffset < 0 || denseOffset > denseSubspaceSize) {
                throw new IllegalArgumentException("Offset out of bounds");
            }

            long restSize = denseOffset;
            long innerSize = denseSubspaceSize;

            for (int i = 0; i < labels.length; ++i) {
                innerSize /= indexedDimensionsSize[i];
                labels[i] = (int) (restSize / innerSize);
                restSize %= innerSize;
            }
        }

        @Override
        public String toString() {
            return "index into " + type;
        }

        private String contentToString(MixedTensor tensor, long maxCells) {
            if (mappedDimensions.size() > 1) throw new IllegalStateException("Should be ensured by caller");
            if (mappedDimensions.isEmpty()) {
                StringBuilder b = new StringBuilder();
                int cellsWritten = denseSubspaceToString(tensor, 0, maxCells, b);
                if (cellsWritten == maxCells && cellsWritten < tensor.size())
                    b.append("...]");
                return b.toString();
            }

            // Exactly 1 mapped dimension
            StringBuilder b = new StringBuilder("{");
            var cellEntries = new ArrayList<>(sparseMap.entrySet());
            cellEntries.sort(Map.Entry.comparingByKey());
            int cellsWritten = 0;
            for (int index = 0; index < cellEntries.size() && cellsWritten < maxCells; index++) {
                if (index > 0)
                    b.append(", ");
                b.append(TensorAddress.labelToString(cellEntries.get(index).getKey().label(0)));
                b.append(":");
                cellsWritten += denseSubspaceToString(tensor, cellEntries.get(index).getValue(), maxCells - cellsWritten, b);
            }
            if (cellsWritten >= maxCells && cellsWritten < tensor.size())
                b.append(", ...");
            b.append("}");
            return b.toString();
        }

        private int denseSubspaceToString(MixedTensor tensor, int subspaceIndex, long maxCells, StringBuilder b) {
            if (maxCells <= 0) {
                return 0;
            }
            if (denseSubspaceSize == 1) {
                b.append(getDouble(subspaceIndex, 0, tensor));
                return 1;
            }
            IndexedTensor.Indexes indexes = IndexedTensor.Indexes.of(denseType);
            int index = 0;
            for (; index < denseSubspaceSize && index < maxCells; index++) {
                indexes.next();
                if (index > 0)
                    b.append(", ");

                // start brackets
                b.append("[".repeat(Math.max(0, indexes.nextDimensionsAtStart())));

                // value
                switch (type.valueType()) {
                    case DOUBLE:   b.append(getDouble(subspaceIndex, index, tensor)); break;
                    case FLOAT:    b.append(getDouble(subspaceIndex, index, tensor)); break; // TODO: Really use floats
                    case BFLOAT16: b.append(getDouble(subspaceIndex, index, tensor)); break;
                    case INT8:     b.append(getDouble(subspaceIndex, index, tensor)); break;
                    default:
                        throw new IllegalStateException("Unexpected value type " + type.valueType());
                }

                // end bracket
                b.append("]".repeat(Math.max(0, indexes.nextDimensionsAtEnd())));
            }
            return index;
        }

        private double getDouble(int subspaceIndex, int denseOffset, MixedTensor tensor) {
            return tensor.index.denseSubspaces.get(subspaceIndex).cells[denseOffset];
        }

        private static class Builder {

            private final Index index;
            private final ImmutableMap.Builder<TensorAddress, Integer> builder = new ImmutableMap.Builder<>();
            private final ImmutableList.Builder<DenseSubspace> listBuilder = new ImmutableList.Builder<>();
            private int count = 0;

            Builder(TensorType type) {
                index = new Index(type);
            }

            void addBlock(DenseSubspace block) {
                if (block.cells.length != index.denseSubspaceSize) {
                    throw new IllegalStateException("dense subspace size mismatch, expected " + index.denseSubspaceSize
                            + " cells, but got: " + block.cells.length);
                }
                builder.put(block.sparseAddress, count++);
                listBuilder.add(block);
            }

            Index build() {
                index.sparseMap = builder.build();
                index.denseSubspaces = listBuilder.build();
                return index;
            }

            Index index() {
                return index;
            }
        }
    }

    private record DenseSubspaceBuilder(TensorType type, double[] values) implements IndexedTensor.DirectIndexBuilder {

        @Override
        public void cellByDirectIndex(long index, double value) {
            values[(int) index] = value;
        }

        @Override
        public void cellByDirectIndex(long index, float value) {
            values[(int) index] = value;
        }

    }

    public static TensorType createPartialType(TensorType.Value valueType, List<TensorType.Dimension> dimensions) {
        TensorType.Builder builder = new TensorType.Builder(valueType);
        for (TensorType.Dimension dimension : dimensions) {
            builder.set(dimension);
        }
        return builder.build();
    }

}
