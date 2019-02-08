// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor_address_ref.h"
#include <vespa/vespalib/stllike/string.h>

namespace vespalib::eval { class ValueType; }

namespace vespalib::tensor {

class TensorAddress;


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
    vespalib::Array<char> _address;

protected:
    void append(vespalib::stringref str) {
        for (size_t i(0); i < str.size() + 1; i++) {
            _address.push_back_fast(str[i]);
        }
    }
    void ensure_room(size_t additional) {
        if (_address.capacity() < (_address.size() + additional)) {
            _address.reserve(_address.size() + additional);
        }
    }
public:
    SparseTensorAddressBuilder();
    void add(vespalib::stringref label) {
        ensure_room(label.size()+1);
        append(label);
    }
    void addUndefined() { _address.push_back('\0'); }
    void clear() { _address.clear(); }
    SparseTensorAddressRef getAddressRef() const {
        return SparseTensorAddressRef(&_address[0], _address.size());
    }
    bool empty() const { return _address.empty(); }
    void populate(const eval::ValueType &type, const TensorAddress &address);
};

}
