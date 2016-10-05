// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include "sparse_tensor_address_ref.h"

namespace vespalib {
namespace tensor {


/**
 * A writer to serialize tensor addresses into a compact representation.
 * All dimensions in the tensors are present, empty label is the "undefined"
 * value.
 *
 * Format: (labelStr NUL)*
 */
class SparseTensorAddressBuilder
{
private:
    std::vector<char> _address;

    void
    append(vespalib::stringref str)
    {
        const char *cstr = str.c_str();
        _address.insert(_address.end(), cstr, cstr + str.size() + 1);
    }
public:
    SparseTensorAddressBuilder()
        : _address()
    {
    }
    void add(vespalib::stringref label) { append(label); }
    void addUndefined() { _address.emplace_back('\0'); }
    void clear() { _address.clear(); }
    SparseTensorAddressRef getAddressRef() const {
        return SparseTensorAddressRef(&_address[0], _address.size());
    }
    bool empty() const { return _address.empty(); }
};


} // namespace vespalib::tensor
} // namespace vespalib
