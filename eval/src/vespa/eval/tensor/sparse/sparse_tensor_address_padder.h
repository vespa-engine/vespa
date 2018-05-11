// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor_address_builder.h"
#include <vespa/eval/eval/value_type.h>
#include <cassert>

namespace vespalib::tensor {

/**
 * This class transforms serialized sparse tensor addresses by padding
 * in "undefined" labels for new dimensions.
 */
class SparseTensorAddressPadder : public SparseTensorAddressBuilder
{
    enum class PadOp { PAD, COPY };

    std::vector<PadOp> _padOps;

public:
    SparseTensorAddressPadder(const eval::ValueType &resultType, const eval::ValueType &inputType);
    SparseTensorAddressPadder(SparseTensorAddressPadder &&) = default;
    SparseTensorAddressPadder & operator =(SparseTensorAddressPadder &&) = default;
    ~SparseTensorAddressPadder();

    void padAddress(SparseTensorAddressRef ref);
};

}

