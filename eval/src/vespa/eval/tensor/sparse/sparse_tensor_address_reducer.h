// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor_address_builder.h"
#include <vespa/eval/tensor/types.h>
#include "sparse_tensor_address_decoder.h"
#include <cassert>

namespace vespalib {
namespace eval { class ValueType; }
namespace tensor {
namespace sparse {

/**
 * Reduce sparse tensor address by removing one or more dimensions.
 */
class TensorAddressReducer : public SparseTensorAddressBuilder
{
    enum AddressOp
    {
        REMOVE,
        COPY
    };

    using AddressOps = std::vector<AddressOp>;

    AddressOps _ops;

public:
    TensorAddressReducer(const eval::ValueType &type,
                         const std::vector<vespalib::string> &removeDimensions);

    ~TensorAddressReducer();

    void reduce(SparseTensorAddressRef ref)
    {
        clear();
        SparseTensorAddressDecoder decoder(ref);
        for (auto op : _ops) {
            switch (op) {
            case AddressOp::REMOVE:
                decoder.skipLabel();
                break;
            case AddressOp::COPY:
                add(decoder.decodeLabel());
            }
        }
        assert(!decoder.valid());
    }
};


} // namespace vespalib::tensor::sparse
} // namespace vespalib::tensor
} // namespace vespalib
