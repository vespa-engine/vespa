// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_tensor_apply.h"
#include "dense_tensor_address_combiner.h"
#include "direct_dense_tensor_builder.h"

namespace vespalib::tensor::dense {

template <typename Function>
std::unique_ptr<Tensor>
apply(DenseTensorAddressCombiner & combiner, DirectDenseTensorBuilder & builder,
      const DenseTensorView &lhs, AddressContext & rhsAddr, const DenseTensorView::CellsRef & rhsCells,
      Function &&func) __attribute__((noinline));

template <typename Function>
std::unique_ptr<Tensor>
apply(DenseTensorAddressCombiner & combiner, DirectDenseTensorBuilder & builder,
      const DenseTensorView &lhs, AddressContext & rhsAddr, const DenseTensorView::CellsRef & rhsCells, Function &&func)
{
    for (DenseTensorCellsIterator lhsItr = lhs.cellsIterator(); lhsItr.valid(); lhsItr.next()) {
        combiner.updateLeftAndCommon(lhsItr.address());
        if (rhsAddr.updateCommon(combiner.address(), combiner.commonRight())) {
            combiner.for_each(rhsAddr, rhsCells, [&func, &builder, &lhsItr](size_t combined, double rhsCell) {
                builder.insertCell(combined, func(lhsItr.cell(), rhsCell));
            });
        }
    }
    return builder.build();
}


template <typename Function>
std::unique_ptr<Tensor>
apply_no_rightonly_dimensions(DenseTensorAddressCombiner & combiner, DirectDenseTensorBuilder & builder,
                              const DenseTensorView &lhs, AddressContext & rhsAddr,
                              const DenseTensorView::CellsRef & rhsCells, Function &&func)  __attribute__((noinline));

template <typename Function>
std::unique_ptr<Tensor>
apply_no_rightonly_dimensions(DenseTensorAddressCombiner & combiner, DirectDenseTensorBuilder & builder,
                              const DenseTensorView &lhs, AddressContext & rhsAddr,
                              const DenseTensorView::CellsRef & rhsCells, Function &&func)
{
    for (DenseTensorCellsIterator lhsItr = lhs.cellsIterator(); lhsItr.valid(); lhsItr.next()) {
        combiner.updateLeftAndCommon(lhsItr.address());
        if (rhsAddr.updateCommon(combiner.address(), combiner.commonRight())) {
            builder.insertCell(combiner.address(), func(lhsItr.cell(), rhsCells[rhsAddr.index()]));
        }
    }
    return builder.build();
}

template <typename Function>
std::unique_ptr<Tensor>
apply(const DenseTensorView &lhs, const DenseTensorView &rhs, Function &&func)
{
    eval::ValueType resultType = DenseTensorAddressCombiner::combineDimensions(lhs.fast_type(), rhs.fast_type());
    DenseTensorAddressCombiner combiner(resultType, lhs.fast_type(), rhs.fast_type());
    DirectDenseTensorBuilder builder(resultType);
    AddressContext rhsAddress(rhs.fast_type());
    if (combiner.hasAnyRightOnlyDimensions()) {
        return apply(combiner, builder, lhs, rhsAddress, rhs.cellsRef(), std::move(func));
    } else {
        return apply_no_rightonly_dimensions(combiner, builder, lhs, rhsAddress, rhs.cellsRef(), std::move(func));
    }
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
