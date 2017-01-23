// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_tensor_apply.h"
#include "dense_tensor_address_combiner.h"
#include "direct_dense_tensor_builder.h"

namespace vespalib {
namespace tensor {
namespace dense {

template <typename Function>
std::unique_ptr<Tensor>
apply(const DenseTensorView &lhs, const DenseTensorView &rhs, Function &&func)
{
    DenseTensorAddressCombiner combiner(lhs.type(), rhs.type());
    DirectDenseTensorBuilder builder(DenseTensorAddressCombiner::combineDimensions(lhs.type(), rhs.type()));
    for (DenseTensorCellsIterator lhsItr = lhs.cellsIterator(); lhsItr.valid(); lhsItr.next()) {
        for (DenseTensorCellsIterator rhsItr = rhs.cellsIterator(); rhsItr.valid(); rhsItr.next()) {
            bool combineSuccess = combiner.combine(lhsItr, rhsItr);
            if (combineSuccess) {
                builder.insertCell(combiner.address(), func(lhsItr.cell(), rhsItr.cell()));
            }
        }
    }
    return builder.build();
}

template <typename Function>
std::unique_ptr<Tensor>
apply(const DenseTensorView &lhs, const Tensor &rhs, Function &&func)
{
    const DenseTensorView *view = dynamic_cast<const DenseTensorView *>(&rhs);
    if (view) {
        return apply(lhs, *view, func);
    }
    const DenseTensor *dense = dynamic_cast<const DenseTensor *>(&rhs);
    if (dense) {
        return apply(lhs, DenseTensorView(*dense), func);
    }
    return Tensor::UP();
}

} // namespace vespalib::tensor::dense
} // namespace vespalib::tensor
} // namespace vespalib
