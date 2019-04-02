// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "types.h"

namespace vespalib::tensor {


class Tensor;
class TensorBuilder;

/**
 * A factory for creating tensors based on stl structures (TensorCells and
 * TensorDimensions) in unit tests.
 */
class TensorFactory {
public:
    static std::unique_ptr<Tensor>
    create(const TensorCells &cells, TensorBuilder &builder);
    static std::unique_ptr<Tensor>
    create(const TensorCells &cells, const TensorDimensions &dimensions, TensorBuilder &builder);
    static std::unique_ptr<Tensor>
    createDense(eval::ValueType::CellType cellType, const DenseTensorCells &cells);
    static std::unique_ptr<Tensor>
    createDense(const DenseTensorCells &cells) {
        return createDense(eval::ValueType::CellType::DOUBLE, cells);
    }
};

}
