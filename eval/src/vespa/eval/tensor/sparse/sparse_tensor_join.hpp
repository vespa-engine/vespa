// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor_join.h"
#include "sparse_tensor_t.h"
#include "sparse_tensor_address_combiner.h"
#include "direct_sparse_tensor_builder.h"

namespace vespalib::tensor::sparse {

template <typename LCT, typename RCT, typename OCT, typename Function>
std::unique_ptr<Tensor>
join(const SparseTensor &lhs_in, const SparseTensor &rhs_in, eval::ValueType res_type, Function &&func)
{
    auto & lhs = static_cast<const SparseTensorT<LCT> &>(lhs_in);
    auto & rhs = static_cast<const SparseTensorT<RCT> &>(rhs_in);
    DirectSparseTensorBuilder<OCT> builder(std::move(res_type));
    TensorAddressCombiner addressCombiner(lhs.fast_type(), rhs.fast_type());
    if (addressCombiner.numOverlappingDimensions() != 0) {
        size_t estimatedCells = std::min(lhs.my_size(), rhs.my_size());
        builder.reserve(estimatedCells*2);
    } else {
        size_t estimatedCells = (lhs.my_size() * rhs.my_size());
        builder.reserve(estimatedCells);
    }
    for (const auto & lhs_kv : lhs.index().get_map()) {
        for (const auto & rhs_kv : rhs.index().get_map()) {
            bool combineSuccess = addressCombiner.combine(lhs_kv.first, rhs_kv.first);
            if (combineSuccess) {
                auto a = lhs.get_value(lhs_kv.second);
                auto b = rhs.get_value(rhs_kv.second);
                builder.insertCell(addressCombiner.getAddressRef(), func(a, b));
            }
        }
    }
    return builder.build();
}

}
