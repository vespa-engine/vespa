// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor_address_reducer.h"
#include "direct_sparse_tensor_builder.h"

namespace vespalib::tensor::sparse {

template <typename Function>
std::unique_ptr<Tensor>
reduceAll(const SparseTensor &tensor,
          DirectSparseTensorBuilder &builder, Function &&func)
{
    auto itr = tensor.my_values().begin();
    auto itrEnd = tensor.my_values().end();
    double result = 0.0;
    if (itr != itrEnd) {
        result = *itr;
        ++itr;
    }
    for (; itr != itrEnd; ++itr) {
        result = func(result, *itr);
    }
    builder.insertCell(SparseTensorAddressBuilder().getAddressRef(), result);
    return builder.build();
}

template <typename Function>
std::unique_ptr<Tensor>
reduceAll(const SparseTensor &tensor, Function &&func)
{
    DirectSparseTensorBuilder builder;
    return reduceAll(tensor, builder, func);
}

template <typename Function>
std::unique_ptr<Tensor>
reduce(const SparseTensor &tensor,
       const std::vector<vespalib::string> &dimensions, Function &&func)
{
    if (dimensions.empty()) {
        return reduceAll(tensor, func);
    }
    DirectSparseTensorBuilder builder(tensor.fast_type().reduce(dimensions));
    if (builder.fast_type().dimensions().empty()) {
        return reduceAll(tensor, builder, func);
    }
    TensorAddressReducer addressReducer(tensor.fast_type(), dimensions);
    builder.reserve(tensor.my_values().size());
    
    for (const auto & kv : tensor.index().get_map()) {
        addressReducer.reduce(kv.first);
        auto v = tensor.my_values()[kv.second];
        builder.insertCell(addressReducer.getAddressRef(), v, func);
    }
    return builder.build();
}

}
