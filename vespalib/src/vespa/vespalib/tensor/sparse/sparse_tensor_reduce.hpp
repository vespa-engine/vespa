// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor_address_reducer.h"
#include <vespa/vespalib/tensor/direct_tensor_builder.h>
#include "direct_sparse_tensor_builder.h"

namespace vespalib {
namespace tensor {
namespace sparse {

template <typename Function>
std::unique_ptr<Tensor>
reduce(const SparseTensor &tensor,
       const std::vector<vespalib::string> &dimensions, Function &&func)
{
    DirectTensorBuilder<SparseTensor> builder(TensorAddressReducer::remainingDimensions(tensor.dimensions(), dimensions));
    TensorAddressReducer addressReducer(tensor.dimensions(), dimensions);
    for (const auto &cell : tensor.cells()) {
        addressReducer.reduce(cell.first);
        builder.insertCell(addressReducer.getAddressRef(), cell.second, func);
    }
    return builder.build();
}

} // namespace vespalib::tensor::sparse
} // namespace vespalib::tensor
} // namespace vespalib
