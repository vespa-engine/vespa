// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_tensor_apply.h"
#include "dense_dimension_combiner.h"
#include "typed_dense_tensor_builder.h"

namespace vespalib::tensor::dense {

template <typename LCT, typename RCT, typename OCT, typename Function>
std::unique_ptr<Tensor>
apply(DenseDimensionCombiner & combiner,
      TypedDenseTensorBuilder<OCT> & builder,
      const ConstArrayRef<LCT> & lhsCells,
      const ConstArrayRef<RCT> & rhsCells, Function &&func) __attribute__((noinline));

template <typename LCT, typename RCT, typename OCT, typename Function>
std::unique_ptr<Tensor>
apply(DenseDimensionCombiner & combiner,
      TypedDenseTensorBuilder<OCT> & builder,
      const ConstArrayRef<LCT> & lhsCells,
      const ConstArrayRef<RCT> & rhsCells, Function &&func)
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

struct CallApply {
    template <typename LCT, typename RCT, typename Function>
    static std::unique_ptr<Tensor>
    call(const ConstArrayRef<LCT> & lhsArr,
         const ConstArrayRef<RCT> & rhsArr,
         DenseDimensionCombiner & combiner,
         Function &&func)
    {
        using OCT = typename OutputCellType<LCT, RCT>::output_type;
        TypedDenseTensorBuilder<OCT> builder(combiner.result_type);
        return apply(combiner, builder, lhsArr, rhsArr, std::move(func));
    }
};

template <typename Function>
std::unique_ptr<Tensor>
apply(const DenseTensorView &lhs, const Tensor &rhs, Function &&func)
{
    const DenseTensorView *view = dynamic_cast<const DenseTensorView *>(&rhs);
    if (view) {
        DenseDimensionCombiner combiner(lhs.fast_type(), view->fast_type());
        TypedCells lhsCells = lhs.cellsRef();
        TypedCells rhsCells = view->cellsRef();
        return dispatch_2<CallApply>(lhsCells, rhsCells, combiner, std::move(func));
    }
    return Tensor::UP();
}

}
