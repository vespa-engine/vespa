// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <limits>
#include <cstdint>

namespace vespalib::eval { class ValueType; }
namespace vespalib { class stringref; }

namespace vespalib::tensor {

class TensorAddress;

/**
 * Utility class for mapping of tensor adress to index
 */
class DenseTensorAddressMapper
{
public:
    static constexpr uint32_t BAD_LABEL = std::numeric_limits<uint32_t>::max();
    static constexpr uint32_t BAD_ADDRESS = std::numeric_limits<uint32_t>::max();
    static uint32_t mapLabelToNumber(stringref label);
    static uint32_t mapAddressToIndex(const TensorAddress &address, const eval::ValueType type);
};

}
