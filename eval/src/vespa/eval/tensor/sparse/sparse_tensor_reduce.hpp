// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor_address_reducer.h"
#include "direct_sparse_tensor_builder.h"

namespace vespalib::tensor::sparse {

template <typename T, typename Function>
std::unique_ptr<Tensor>
reduceAll(const SparseTensorT<T> &tensor, Function &&func)
{
    DirectSparseTensorBuilder<double> builder;
    size_t sz = tensor.my_size();
    double result = 0.0;
    if (sz != 0) {
        result = tensor.get_value(0);
    }
    for (size_t i = 1; i < sz; ++i) {
        result = func(result, tensor.get_value(i));
    }
    builder.insertCell(SparseTensorAddressRef(), result);
    return builder.build();
}

template <typename T, typename Function>
std::unique_ptr<Tensor>
reduce(const SparseTensorT<T> &tensor,
       const std::vector<vespalib::string> &dimensions, Function &&func)
{
    auto tt = tensor.fast_type().reduce(dimensions);
    if (tt.is_double()) {
        return reduceAll(tensor, func);
    }
    DirectSparseTensorBuilder<T> builder(std::move(tt));
    builder.reserve(tensor.my_size());
    TensorAddressReducer addressReducer(tensor.fast_type(), dimensions);
    for (const auto & kv : tensor.index().get_map()) {
        addressReducer.reduce(kv.first);
        auto v = tensor.get_value(kv.second);
        builder.insertCell(addressReducer.getAddressRef(), v, func);
    }
    return builder.build();
}

}
