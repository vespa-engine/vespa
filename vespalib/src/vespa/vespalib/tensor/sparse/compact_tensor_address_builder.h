// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include "compact_tensor_address_ref.h"

namespace vespalib {
namespace tensor {


/**
 * A writer to serialize tensor addresses into a compact representation.
 *
 * Format: (dimStr NUL labelStr NUL)*
 */
class CompactTensorAddressBuilder
{
private:
    std::vector<char> _address;
public:
    CompactTensorAddressBuilder();
    void add(vespalib::stringref dimension, vespalib::stringref label);
    void clear() { _address.clear(); }
    CompactTensorAddressRef getAddressRef() const {
        return CompactTensorAddressRef(&_address[0], _address.size());
    }
    bool empty() const { return _address.empty(); }
};


} // namespace vespalib::tensor
} // namespace vespalib
