// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_tensor.h"
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/eval/tensor/tensor_builder.h>

namespace vespalib::tensor {

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
    DenseTensor::Cells     _cells;
    std::vector<size_t>    _addressBuilder;
    std::vector<Dimension> _dimensionsMapping;

    void allocateCellsStorage();
    void sortDimensions();
    size_t calculateCellAddress();

public:
    DenseTensorBuilder();
    ~DenseTensorBuilder();

    Dimension defineDimension(const vespalib::string &dimension, size_t dimensionSize);
    DenseTensorBuilder &addLabel(Dimension dimension, size_t label);
    DenseTensorBuilder &addCell(double value);
    std::unique_ptr<DenseTensor> build();
};

}

