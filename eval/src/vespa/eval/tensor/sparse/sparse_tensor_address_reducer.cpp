// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_address_reducer.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/vespalib/stllike/hash_set.hpp>

namespace vespalib::tensor::sparse {

TensorAddressReducer::TensorAddressReducer(const eval::ValueType &type,
                                           const std::vector<vespalib::string> & removeDimensions)
    : SparseTensorAddressBuilder(),
      _ops()
{
    TensorDimensionsSet removeSet(removeDimensions.cbegin(), removeDimensions.cend());
    _ops.reserve(type.dimensions().size());
    for (const auto &dim : type.dimensions()) {
        if (removeSet.find(dim.name) != removeSet.end()) {
            _ops.push_back(AddressOp::REMOVE);
        } else {
            _ops.push_back(AddressOp::COPY);
        }
    }
}

TensorAddressReducer::~TensorAddressReducer() = default;

}

