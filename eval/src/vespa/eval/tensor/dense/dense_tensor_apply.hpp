// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_tensor_apply.h"
#include "dense_dimension_combiner.h"
#include "direct_dense_tensor_builder.h"

namespace vespalib::tensor::dense {

template <typename Function>
std::unique_ptr<Tensor>
apply(DenseDimensionCombiner & combiner, DirectDenseTensorBuilder & builder,
      const DenseTensorView::CellsRef & lhsCells,
      const DenseTensorView::CellsRef & rhsCells, Function &&func) __attribute__((noinline));

template <typename Function>
std::unique_ptr<Tensor>
apply(DenseDimensionCombiner & combiner, DirectDenseTensorBuilder & builder,
      const DenseTensorView::CellsRef & lhsCells,
      const DenseTensorView::CellsRef & rhsCells, Function &&func)
{
    for (combiner.leftReset(); combiner.leftInRange(); combiner.stepLeft()) {
        for (combiner.rightReset(); combiner.rightInRange(); combiner.stepRight()) {
            for (combiner.commonReset(); combiner.commonInRange(); combiner.stepCommon()) {
                size_t outIdx = combiner.outputIdx();
                size_t l = combiner.leftIdx();
                size_t r = combiner.rightIdx();
                builder.insertCell(outIdx, func(lhsCells[l], rhsCells[r]));
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
        DenseDimensionCombiner combiner(lhs.fast_type(), view->fast_type());
        DirectDenseTensorBuilder builder(combiner.result_type);
        return apply(combiner, builder, lhs.cellsRef(), view->cellsRef(), std::move(func));
    }
    return Tensor::UP();
}

}
