// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_address_reducer.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/vespalib/stllike/hash_set.hpp>

namespace vespalib {
namespace tensor {
namespace sparse {

TensorAddressReducer::TensorAddressReducer(const eval::ValueType &type,
                                           const std::vector<vespalib::string> &
                                           removeDimensions)
    : SparseTensorAddressBuilder(),
      _ops()
{
    TensorDimensionsSet removeSet(removeDimensions.cbegin(),
                                  removeDimensions.cend());
    _ops.reserve(type.dimensions().size());
    for (auto &dim : type.dimensions()) {
        if (removeSet.find(dim.name) != removeSet.end()) {
            _ops.push_back(AddressOp::REMOVE);
        } else {
            _ops.push_back(AddressOp::COPY);
        }
    }
}

TensorAddressReducer::~TensorAddressReducer()
{
}

} // namespace vespalib::tensor::sparse
} // namespace vespalib::tensor
} // namespace vespalib
