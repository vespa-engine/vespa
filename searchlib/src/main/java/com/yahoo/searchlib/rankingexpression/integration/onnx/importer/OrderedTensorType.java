// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.onnx.importer;

import com.yahoo.tensor.TensorType;
import onnx.Onnx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A Vespa tensor type is ordered by the lexicographical ordering of dimension
 * names. ONNX tensors have an explicit ordering of their dimensions.
 * During import, we need to track the Vespa dimension that matches the
 * corresponding ONNX dimension as the ordering can change after
 * dimension renaming. That is the purpose of this class.
 *
 * @author lesters
 */
public class OrderedTensorType {

    private final TensorType type;
    private final List<TensorType.Dimension> dimensions;

    private final long[] innerSizesOnnx;
    private final long[] innerSizesVespa;
    private final int[] dimensionMap;

    private OrderedTensorType(List<TensorType.Dimension> dimensions) {
        this.dimensions = Collections.unmodifiableList(dimensions);
        this.type = new TensorType.Builder(dimensions).build();
        this.innerSizesOnnx = new long[dimensions.size()];
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
        innerSizesOnnx[numDimensions - 1] = 1;
        innerSizesVespa[numDimensions - 1] = 1;
        for (int i = numDimensions - 1; --i >= 0; ) {
            innerSizesOnnx[i] = dimensions().get(i+1).size().orElse(-1L) * innerSizesOnnx[i+1];
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

    /**
     * When dimension ordering between Vespa and Onnx differs, i.e.
     * after dimension renaming, use the dimension map to read in values
     * so that they are correctly laid out in memory for Vespa.
     * Used when importing tensors from Onnx.
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
            long address = rest / innerSizesOnnx[i];
            directIndex += innerSizesVespa[dimensionMap[i]] * address;
            rest %= innerSizesOnnx[i];
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

    public void verifyType(Onnx.TypeProto typeProto) {
        Onnx.TensorShapeProto shape = typeProto.getTensorType().getShape();
        if (shape != null) {
            if (shape.getDimCount() != type.rank()) {
                throw new IllegalArgumentException("Onnx shape of does not match Vespa shape");
            }
            for (int onnxIndex = 0; onnxIndex < dimensions.size(); ++onnxIndex) {
                int vespaIndex = dimensionMap[onnxIndex];
                Onnx.TensorShapeProto.Dimension onnxDimension = shape.getDim(onnxIndex);
                TensorType.Dimension vespaDimension = type().dimensions().get(vespaIndex);
                if (onnxDimension.getDimValue() != vespaDimension.size().orElse(-1L)) {
                    throw new IllegalArgumentException("TensorFlow dimensions of does not match Vespa dimensions");
                }
            }
        }
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

    public static OrderedTensorType fromOnnxType(Onnx.TypeProto type) {
        return fromOnnxType(type, "d");  // standard naming convention: d0, d1, ...
    }

    public static OrderedTensorType fromOnnxType(Onnx.TypeProto type, String dimensionPrefix) {
        Onnx.TensorShapeProto shape = type.getTensorType().getShape();
        Builder builder = new Builder(shape);
        for (int i = 0; i < shape.getDimCount(); ++ i) {
            String dimensionName = dimensionPrefix + i;
            Onnx.TensorShapeProto.Dimension onnxDimension = shape.getDim(i);
            if (onnxDimension.getDimValue() >= 0) {
                builder.add(TensorType.Dimension.indexed(dimensionName, onnxDimension.getDimValue()));
            } else {
                builder.add(TensorType.Dimension.indexed(dimensionName));
            }
        }
        return builder.build();
    }

    public static OrderedTensorType fromOnnxType(List<Long> dims, String dimensionPrefix) {
        Builder builder = new Builder();
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

    public static OrderedTensorType standardType(OrderedTensorType type) {
        Builder builder = new Builder();
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

    public static class Builder {

        private final Onnx.TensorShapeProto shape;
        private final List<TensorType.Dimension> dimensions;

        public Builder(Onnx.TensorShapeProto shape) {
            this.shape = shape;
            this.dimensions = new ArrayList<>(shape.getDimCount());
        }

        public Builder() {
            this.shape = null;
            this.dimensions = new ArrayList<>();
        }

        public Builder add(TensorType.Dimension vespaDimension) {
            if (shape != null) {
                int index = dimensions.size();
                Onnx.TensorShapeProto.Dimension onnxDimension = shape.getDim(index);
                long size = onnxDimension.getDimValue();
                if (size >= 0) {
                    if (vespaDimension.type() != TensorType.Dimension.Type.indexedBound) {
                        throw new IllegalArgumentException("Non-agreement between Onnx and Vespa " +
                                "dimension types");
                    }
                    if (!vespaDimension.size().isPresent()) {
                        throw new IllegalArgumentException("Tensor dimension is indexed bound but does " +
                                "not have a size");
                    }
                    if (vespaDimension.size().get() != size) {
                        throw new IllegalArgumentException("Non-agreement between Onnx and Vespa " +
                                "dimension sizes. TensorFlow: " + size + " Vespa: " +
                                vespaDimension.size().get());
                    }
                } else {
                    if (vespaDimension.type() != TensorType.Dimension.Type.indexedUnbound) {
                        throw new IllegalArgumentException("Non-agreement between Onnx and Vespa " +
                                "dimension types");
                    }
                }
            }
            this.dimensions.add(vespaDimension);
            return this;
        }

        public OrderedTensorType build() {
            return new OrderedTensorType(dimensions);
        }
    }

}
