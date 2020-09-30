// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_generic_join.h"
#include "dense_dimension_combiner.h"
#include "typed_dense_tensor_builder.h"

namespace vespalib::tensor::dense {

template <typename LCT, typename RCT, typename OCT, typename Function>
std::unique_ptr<Tensor>
generic_join(DenseDimensionCombiner & combiner,
      TypedDenseTensorBuilder<OCT> & builder,
      const ConstArrayRef<LCT> & lhsCells,
      const ConstArrayRef<RCT> & rhsCells, Function &&func) __attribute__((noinline));

template <typename LCT, typename RCT, typename OCT, typename Function>
std::unique_ptr<Tensor>
generic_join(DenseDimensionCombiner & combiner,
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

struct CallGenericJoin {
    template <typename LCT, typename RCT, typename Function>
    static std::unique_ptr<Tensor>
    call(const ConstArrayRef<LCT> & lhsArr,
         const ConstArrayRef<RCT> & rhsArr,
         DenseDimensionCombiner & combiner,
         Function &&func)
    {
        using OCT = typename eval::UnifyCellTypes<LCT, RCT>::type;
        TypedDenseTensorBuilder<OCT> builder(combiner.result_type);
        return generic_join(combiner, builder, lhsArr, rhsArr, std::move(func));
    }
};

template <typename Function>
std::unique_ptr<Tensor>
generic_join(const DenseTensorView &lhs, const Tensor &rhs, Function &&func)
{
    DenseDimensionCombiner combiner(lhs.fast_type(), rhs.type());
    TypedCells lhsCells = lhs.cells();
    TypedCells rhsCells = rhs.cells();
    return dispatch_2<CallGenericJoin>(lhsCells, rhsCells, combiner, std::move(func));
}

}
