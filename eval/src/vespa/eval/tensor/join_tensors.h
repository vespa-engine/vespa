// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor.h"
#include "direct_tensor_builder.h"

namespace vespalib::tensor {

/*
 * Join the cells of two tensors.
 * The given function is used to calculate the resulting cell value for overlapping cells.
 */
template <typename TensorImplType, typename Function>
Tensor::UP
joinTensors(const TensorImplType &lhs,
            const TensorImplType &rhs,
            Function &&func)
{
    DirectTensorBuilder<TensorImplType>
        builder(lhs.combineDimensionsWith(rhs), lhs.cells());
    for (const auto &rhsCell : rhs.cells()) {
        builder.insertCell(rhsCell.first, rhsCell.second, func);
    }
    return builder.build();
}

/*
 * Join the cells of two tensors, where the rhs values are treated as negated values.
 * The given function is used to calculate the resulting cell value for overlapping cells.
 */
template <typename TensorImplType, typename Function>
Tensor::UP
joinTensorsNegated(const TensorImplType &lhs,
                   const TensorImplType &rhs,
                   Function &&func)
{
    DirectTensorBuilder<TensorImplType>
        builder(lhs.combineDimensionsWith(rhs), lhs.cells());
    for (const auto &rhsCell : rhs.cells()) {
        builder.insertCell(rhsCell.first, -rhsCell.second, func);
    }
    return builder.build();
}

}
