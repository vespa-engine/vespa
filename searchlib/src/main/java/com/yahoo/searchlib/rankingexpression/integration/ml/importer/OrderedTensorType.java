// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchlib.rankingexpression.integration.ml.importer;

import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.TensorTypeParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A Vespa tensor type is ordered by the lexicographical ordering of dimension
 * names. Imported tensors have an explicit ordering of their dimensions.
 * During import, we need to track the Vespa dimension that matches the
 * corresponding imported dimension as the ordering can change after
 * dimension renaming. That is the purpose of this class.
 *
 * @author lesters
 */
public class OrderedTensorType {

    private final TensorType type;
    private final List<TensorType.Dimension> dimensions;

    private final long[] innerSizesOriginal;
    private final long[] innerSizesVespa;
    private final int[] dimensionMap;

    private OrderedTensorType(List<TensorType.Dimension> dimensions) {
        this.dimensions = Collections.unmodifiableList(dimensions);
        this.type = new TensorType.Builder(dimensions).build();
        this.innerSizesOriginal = new long[dimensions.size()];
        this.innerSizesVespa = new long[dimensions.size()];
        this.dimensionMap = createDimensionMap();
    }

    public TensorType type() { return this.type; }

    public int rank() { return dimensions.size(); }

    public List<TensorType.Dimension> dimensions() {
        return dimensions;
    }

    public List<String> dimensionNames() {
        return dimensions.stream().map(TensorType.Dimension::name).collect(Collectors.toList());
    }

    private int[] createDimensionMap() {
        int numDimensions = dimensions.size();
        if (numDimensions == 0) {
            return null;
        }
        innerSizesOriginal[numDimensions - 1] = 1;
        innerSizesVespa[numDimensions - 1] = 1;
        for (int i = numDimensions - 1; --i >= 0; ) {
            innerSizesOriginal[i] = dimensions().get(i+1).size().orElse(-1L) * innerSizesOriginal[i+1];
            innerSizesVespa[i] = type.dimensions().get(i+1).size().orElse(-1L) * innerSizesVespa[i+1];
        }
        int[] mapping = new int[numDimensions];
        for (int i = 0; i < numDimensions; ++i) {
            TensorType.Dimension dim1 = dimensions().get(i);
            for (int j = 0; j < numDimensions; ++j) {
                TensorType.Dimension dim2 = type.dimensions().get(j);
                if (dim1.equals(dim2)) {
                    mapping[i] = j;
                    break;
                }
            }
        }
        return mapping;
    }

    public int dimensionMap(int originalIndex) {
        return dimensionMap[originalIndex];
    }

    /**
     * When dimension ordering between Vespa and imported differs, i.e.
     * after dimension renaming, use the dimension map to read in values
     * so that they are correctly laid out in memory for Vespa.
     * Used when importing tensors.
     */
    public int toDirectIndex(int index) {
        if (dimensions.size() == 0) {
            return 0;
        }
        if (dimensionMap == null)  {
            throw new IllegalArgumentException("Dimension map is not available");
        }
        int directIndex = 0;
        long rest = index;
        for (int i = 0; i < dimensions.size(); ++i) {
            long address = rest / innerSizesOriginal[i];
            directIndex += innerSizesVespa[dimensionMap[i]] * address;
            rest %= innerSizesOriginal[i];
        }
        return directIndex;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof OrderedTensorType)) {
            return false;
        }
        OrderedTensorType other = (OrderedTensorType) obj;
        if (dimensions.size() != dimensions.size()) {
            return false;
        }
        List<TensorType.Dimension> thisDimensions = this.dimensions();
        List<TensorType.Dimension> otherDimensions = other.dimensions();
        for (int i = 0; i < thisDimensions.size(); ++i) {
            if (!thisDimensions.get(i).equals(otherDimensions.get(i))) {
                return false;
            }
        }
        return true;
    }

    public OrderedTensorType rename(DimensionRenamer renamer) {
        List<TensorType.Dimension> renamedDimensions = new ArrayList<>(dimensions.size());
        for (TensorType.Dimension dimension : dimensions) {
            String oldName = dimension.name();
            Optional<String> newName = renamer.dimensionNameOf(oldName);
            if (!newName.isPresent())
                return this; // presumably, already renamed
            TensorType.Dimension.Type dimensionType = dimension.type();
            if (dimensionType == TensorType.Dimension.Type.indexedBound) {
                renamedDimensions.add(TensorType.Dimension.indexed(newName.get(), dimension.size().get()));
            } else if (dimensionType == TensorType.Dimension.Type.indexedUnbound) {
                renamedDimensions.add(TensorType.Dimension.indexed(newName.get()));
            } else if (dimensionType == TensorType.Dimension.Type.mapped) {
                renamedDimensions.add(TensorType.Dimension.mapped(newName.get()));
            }
        }
        return new OrderedTensorType(renamedDimensions);
    }

    public OrderedTensorType rename(String dimensionPrefix) {
        OrderedTensorType.Builder builder = new OrderedTensorType.Builder();
        for (int i = 0; i < dimensions.size(); ++ i) {
            String dimensionName = dimensionPrefix + i;
            Optional<Long> dimSize = dimensions.get(i).size();
            if (dimSize.isPresent() && dimSize.get() >= 0) {
                builder.add(TensorType.Dimension.indexed(dimensionName, dimSize.get()));
            } else {
                builder.add(TensorType.Dimension.indexed(dimensionName));
            }
        }
        return builder.build();
    }

    public static OrderedTensorType standardType(OrderedTensorType type) {
        OrderedTensorType.Builder builder = new OrderedTensorType.Builder();
        for (int i = 0; i < type.dimensions().size(); ++ i) {
            TensorType.Dimension dim = type.dimensions().get(i);
            String dimensionName = "d" + i;
            if (dim.size().isPresent() && dim.size().get() >= 0) {
                builder.add(TensorType.Dimension.indexed(dimensionName, dim.size().get()));
            } else {
                builder.add(TensorType.Dimension.indexed(dimensionName));
            }
        }
        return builder.build();
    }

    public static Long tensorSize(TensorType type) {
        Long size = 1L;
        for (TensorType.Dimension dimension : type.dimensions()) {
            size *= dimensionSize(dimension);
        }
        return size;
    }

    public static Long dimensionSize(TensorType.Dimension dim) {
        return dim.size().orElseThrow(() -> new IllegalArgumentException("Dimension has no size"));
    }

    /**
     * Returns a string representation of this: A standard tensor type string where dimensions
     * are listed in the order of this rather than in the natural order of their names.
     */
    @Override
    public String toString() {
        return "tensor(" + dimensions.stream().map(TensorType.Dimension::toString).collect(Collectors.joining(",")) + ")";
    }

    /**
     * Creates an instance from the string representation of this: A standard tensor type string
     * where dimensions are listed in the order of this rather than the natural order of their names.
     */
    public static OrderedTensorType fromSpec(String typeSpec) {
        return new OrderedTensorType(TensorTypeParser.dimensionsFromSpec(typeSpec));
    }

    public static OrderedTensorType fromDimensionList(List<Long> dims) {
        return fromDimensionList(dims, "d");  // standard naming convention: d0, d1, ...
    }

    public static OrderedTensorType fromDimensionList(List<Long> dims, String dimensionPrefix) {
        OrderedTensorType.Builder builder = new OrderedTensorType.Builder();
        for (int i = 0; i < dims.size(); ++ i) {
            String dimensionName = dimensionPrefix + i;
            Long dimSize = dims.get(i);
            if (dimSize >= 0) {
                builder.add(TensorType.Dimension.indexed(dimensionName, dimSize));
            } else {
                builder.add(TensorType.Dimension.indexed(dimensionName));
            }
        }
        return builder.build();
    }

    public static class Builder {

        private final List<TensorType.Dimension> dimensions;

        public Builder() {
            this.dimensions = new ArrayList<>();
        }

        public Builder add(TensorType.Dimension vespaDimension) {
            this.dimensions.add(vespaDimension);
            return this;
        }

        public OrderedTensorType build() {
            return new OrderedTensorType(dimensions);
        }
    }

}
