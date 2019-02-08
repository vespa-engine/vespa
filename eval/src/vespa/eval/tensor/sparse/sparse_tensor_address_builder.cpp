// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_address_builder.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/tensor/tensor_address.h>
#include <vespa/eval/tensor/tensor_address_element_iterator.h>

namespace vespalib::tensor {

SparseTensorAddressBuilder::SparseTensorAddressBuilder()
    : _address()
{
}

void
SparseTensorAddressBuilder::populate(const eval::ValueType &type, const TensorAddress &address)
{
    clear();
    TensorAddressElementIterator itr(address);
    for (const auto &dimension : type.dimensions()) {
        if (itr.skipToDimension(dimension.name)) {
            add(itr.label());
        } else {
            addUndefined();
        }
    }
}

}
