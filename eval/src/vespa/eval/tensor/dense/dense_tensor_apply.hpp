// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_tensor_apply.h"
#include "dense_tensor_address_combiner.h"
#include "direct_dense_tensor_builder.h"

namespace vespalib::tensor::dense {

template <typename Function>
std::unique_ptr<Tensor>
apply(const DenseTensorView &lhs, const DenseTensorView &rhs, Function &&func)
{
    DenseTensorAddressCombiner combiner(lhs.fast_type(), rhs.fast_type());
    CommonDenseTensorCellsIterator rhsIter(combiner.commonRight(), rhs.fast_type(), rhs.cellsRef());
    DirectDenseTensorBuilder builder(DenseTensorAddressCombiner::combineDimensions(lhs.fast_type(), rhs.fast_type()));
    for (DenseTensorCellsIterator lhsItr = lhs.cellsIterator(); lhsItr.valid(); lhsItr.next()) {
        combiner.updateLeftAndCommon(lhsItr.address());
        if (rhsIter.updateCommon(combiner.address())) {
            rhsIter.for_each([&combiner, &func, &builder, &lhsItr](const DenseTensorCellsIterator::Address & right, double rhsCell) {
                combiner.updateRight(right);
                builder.insertCell(combiner.address(), func(lhsItr.cell(), rhsCell));
            });
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

}
