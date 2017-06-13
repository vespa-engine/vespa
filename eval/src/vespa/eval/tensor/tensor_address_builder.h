// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_address.h"

namespace vespalib {
namespace tensor {


/**
 * A builder for tensor addresses.
 */
class TensorAddressBuilder
{
    TensorAddress::Elements _elements;
public:
    TensorAddressBuilder()
        : _elements()
    {
    }
    void add(vespalib::stringref dimension, vespalib::stringref label) {
        _elements.emplace_back(dimension, label);
    }
    TensorAddress build() { return TensorAddress(_elements); }
    void clear(void) { _elements.clear(); }
};


} // namespace vespalib::tensor
} // namespace vespalib
