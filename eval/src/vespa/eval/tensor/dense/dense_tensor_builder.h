// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_tensor.h"
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/eval/tensor/tensor_builder.h>

namespace vespalib {
namespace tensor {

/**
 * A builder of for dense tensors.
 */
class DenseTensorBuilder
{
public:
    using Dimension = TensorBuilder::Dimension;

private:
    vespalib::hash_map<vespalib::string, size_t> _dimensionsEnum;
    std::vector<eval::ValueType::Dimension> _dimensions;
    DenseTensor::Cells _cells;
    std::vector<size_t> _addressBuilder;
    std::vector<Dimension> _dimensionsMapping;

    void allocateCellsStorage();
    void sortDimensions();
    size_t calculateCellAddress();

public:
    DenseTensorBuilder();

    Dimension defineDimension(const vespalib::string &dimension, size_t dimensionSize);
    DenseTensorBuilder &addLabel(Dimension dimension, size_t label);
    DenseTensorBuilder &addCell(double value);
    Tensor::UP build();
};

} // namespace vespalib::tensor
} // namespace vespalib
