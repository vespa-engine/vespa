// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "sparse_tensor_address_reducer.h"

namespace vespalib {
namespace tensor {
namespace sparse {

TensorAddressReducer::TensorAddressReducer(const TensorDimensions &dims,
                                           const std::vector<vespalib::string> &
                                           removeDimensions)
    : SparseTensorAddressBuilder(),
      _ops()
{
    TensorDimensionsSet removeSet(removeDimensions.cbegin(),
                                  removeDimensions.cend());
    _ops.reserve(dims.size());
    for (auto &dim : dims) {
        if (removeSet.find(dim) != removeSet.end()) {
            _ops.push_back(AddressOp::REMOVE);
        } else {
            _ops.push_back(AddressOp::COPY);
        }
    }
}

TensorDimensions
TensorAddressReducer::remainingDimensions(const TensorDimensions &dimensions,
                                          const std::vector<vespalib::string> &
                                          removeDimensions)
{
    TensorDimensionsSet removeSet(removeDimensions.cbegin(),
                                  removeDimensions.cend());
    TensorDimensions result;
    result.reserve(dimensions.size());
    for (auto &dim : dimensions) {
        if (removeSet.find(dim) == removeSet.end()) {
            result.push_back(dim);
        }
    }
    return std::move(result);
}

TensorAddressReducer::~TensorAddressReducer()
{
}

} // namespace vespalib::tensor::sparse
} // namespace vespalib::tensor
} // namespace vespalib
