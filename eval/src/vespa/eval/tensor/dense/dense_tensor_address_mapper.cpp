// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_address_mapper.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/tensor/tensor_address.h>
#include <vespa/eval/tensor/tensor_address_element_iterator.h>

namespace vespalib::tensor {

uint32_t
DenseTensorAddressMapper::mapLabelToNumber(stringref label)
{
    uint32_t result = 0;
    for (char c : label) {
        if (c < '0' || c > '9') {
            return BAD_LABEL; // bad char
        }
        result = result * 10 + (c - '0');
        if (result > 100000000) {
            return BAD_LABEL; // overflow
        }
    }
    return result;
}

uint32_t
DenseTensorAddressMapper::mapAddressToIndex(const TensorAddress &address, const eval::ValueType &type)
{
    uint32_t idx = 0;
    TensorAddressElementIterator<TensorAddress> addressIterator(address);
    for (const auto &dimension : type.dimensions()) {
        if (addressIterator.skipToDimension(dimension.name)) {
            uint32_t label = mapLabelToNumber(addressIterator.label());
            if (label == BAD_LABEL || label >= dimension.size) {
                return BAD_ADDRESS;
            }
            idx = idx * dimension.size + label;
            addressIterator.next();
        } else {
            // output dimension not in input
            idx = idx * dimension.size;
        }
    }
    return idx;
}

}
