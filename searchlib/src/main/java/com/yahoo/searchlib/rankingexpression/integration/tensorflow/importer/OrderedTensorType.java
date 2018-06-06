// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer;

import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.TensorTypeParser;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.NodeDef;
import org.tensorflow.framework.TensorShapeProto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A Vespa tensor type is ordered by the lexicographical ordering of dimension
 * names. TensorFlow tensors have an explicit ordering of their dimensions.
 * During import, we need to track the Vespa dimension that matches the
 * corresponding TensorFlow dimension as the ordering can change after
 * dimension renaming. That is the purpose of this class.
 *
 * @author lesters
 */
public class OrderedTensorType {

    private final TensorType type;
    private final List<TensorType.Dimension> dimensions;

    private final long[] innerSizesTensorFlow;
    private final long[] innerSizesVespa;
    private final int[] dimensionMap;

    private OrderedTensorType(List<TensorType.Dimension> dimensions) {
        this.dimensions = Collections.unmodifiableList(dimensions);
        this.type = new TensorType.Builder(dimensions).build();
        this.innerSizesTensorFlow = new long[dimensions.size()];
        this.innerSizesVespa = new long[dimensions.size()];
        this.dimensionMap = createDimensionMap();
    }

    public TensorType type() {
        return this.type;
    }

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
        innerSizesTensorFlow[numDimensions - 1] = 1;
        innerSizesVespa[numDimensions - 1] = 1;
        for (int i = numDimensions - 1; --i >= 0; ) {
            innerSizesTensorFlow[i] = dimensions().get(i+1).size().orElse(-1L) * innerSizesTensorFlow[i+1];
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
     * When dimension ordering between Vespa and TensorFlow differs, i.e.
     * after dimension renaming, use the dimension map to read in values
     * so that they are correctly laid out in memory for Vespa.
     * Used when importing tensors from TensorFlow.
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
            long address = rest / innerSizesTensorFlow[i];
            directIndex += innerSizesVespa[dimensionMap[i]] * address;
            rest %= innerSizesTensorFlow[i];
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

    public void verifyType(NodeDef node) {
        TensorShapeProto shape = tensorFlowShape(node);
        if (shape != null) {
            if (shape.getDimCount() != type.rank()) {
                throw new IllegalArgumentException("TensorFlow shape of '" + node.getName() + "' " +
                                                   "does not match Vespa shape");
            }
            for (int tensorFlowIndex = 0; tensorFlowIndex < dimensions.size(); ++tensorFlowIndex) {
                int vespaIndex = dimensionMap[tensorFlowIndex];
                TensorShapeProto.Dim tensorFlowDimension = shape.getDim(tensorFlowIndex);
                TensorType.Dimension vespaDimension = type().dimensions().get(vespaIndex);
                if (tensorFlowDimension.getSize() != vespaDimension.size().orElse(-1L)) {
                    throw new IllegalArgumentException("TensorFlow dimensions of '" + node.getName() + "' " +
                                                       "does not match Vespa dimensions");
                }
            }
        }
    }

    private static TensorShapeProto tensorFlowShape(NodeDef node) {
        AttrValue attrValueList = node.getAttrMap().get("_output_shapes");
        if (attrValueList == null) {
            throw new IllegalArgumentException("_output_shapes attribute of '" + node.getName() + "' " +
                                               "does not exist");
        }
        if (attrValueList.getValueCase() != AttrValue.ValueCase.LIST) {
            throw new IllegalArgumentException("_output_shapes attribute of '" + node.getName() + "' " +
                                               "is not of expected type");
        }
        List<TensorShapeProto> shapeList = attrValueList.getList().getShapeList();
        return shapeList.get(0); // support multiple outputs?
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

    public static OrderedTensorType fromTensorFlowType(NodeDef node) {
        return fromTensorFlowType(node, "d");  // standard naming convention: d0, d1, ...
    }

    public static OrderedTensorType fromTensorFlowType(NodeDef node, String dimensionPrefix) {
        Builder builder = new Builder(node);
        TensorShapeProto shape = tensorFlowShape(node);
        for (int i = 0; i < shape.getDimCount(); ++ i) {
            String dimensionName = dimensionPrefix + i;
            TensorShapeProto.Dim tensorFlowDimension = shape.getDim(i);
            if (tensorFlowDimension.getSize() >= 0) {
                builder.add(TensorType.Dimension.indexed(dimensionName, tensorFlowDimension.getSize()));
            } else {
                builder.add(TensorType.Dimension.indexed(dimensionName));
            }
        }
        return builder.build();
    }

    public static class Builder {

        private final TensorShapeProto shape;
        private final List<TensorType.Dimension> dimensions;

        public Builder(NodeDef node) {
            this.shape = tensorFlowShape(node);
            this.dimensions = new ArrayList<>(shape.getDimCount());
        }

        public Builder add(TensorType.Dimension vespaDimension) {
            int index = dimensions.size();
            TensorShapeProto.Dim tensorFlowDimension = shape.getDim(index);
            long size = tensorFlowDimension.getSize();
            if (size >= 0) {
                if (vespaDimension.type() != TensorType.Dimension.Type.indexedBound) {
                    throw new IllegalArgumentException("Non-agreement between TensorFlow and Vespa " +
                                                       "dimension types");
                }
                if (!vespaDimension.size().isPresent()) {
                    throw new IllegalArgumentException("Tensor dimension is indexed bound but does " +
                                                       "not have a size");
                }
                if (vespaDimension.size().get() != size) {
                    throw new IllegalArgumentException("Non-agreement between TensorFlow and Vespa " +
                                                       "dimension sizes. TensorFlow: " + size + " Vespa: " +
                                                       vespaDimension.size().get());
                }
            } else {
                if (vespaDimension.type() != TensorType.Dimension.Type.indexedUnbound) {
                    throw new IllegalArgumentException("Non-agreement between TensorFlow and Vespa " +
                                                       "dimension types");
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
