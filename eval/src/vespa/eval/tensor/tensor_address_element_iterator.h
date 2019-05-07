// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib::tensor {


/**
 * An iterator for tensor address elements used to simplify 3-way merge
 * between two tensor addresses and a dimension vector.
 */
template <class Address>
class TensorAddressElementIterator {
    using InnerIterator = typename Address::Elements::const_iterator;
    InnerIterator _itr;
    InnerIterator _itrEnd;
public:
    TensorAddressElementIterator(const Address &address)
        : _itr(address.elements().cbegin()),
          _itrEnd(address.elements().cend())
    {
    }
    bool valid() const { return (_itr != _itrEnd); }
    vespalib::stringref dimension() const { return _itr->dimension(); }
    vespalib::stringref label() const { return _itr->label(); }
    void next() { ++_itr; }
    bool skipToDimension(vespalib::stringref rhsDimension) {
        for (;;) {
            if (!valid()) {
                return false;
            }
            if (dimension() < rhsDimension) {
                next();
            } else {
                return (dimension() == rhsDimension);
            }
        }
    }
};

}
