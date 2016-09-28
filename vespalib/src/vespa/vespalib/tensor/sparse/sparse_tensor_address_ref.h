// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <vespa/vespalib/util/stash.h>

namespace vespalib {

// From vespalib/util/hashmap.h
size_t hashValue(const void * buf, size_t sz);

namespace tensor {

/**
 * A reference to a compact sparse immutable address to a tensor cell.
 */
class SparseTensorAddressRef
{
    const void *_start;
    size_t _size;
    size_t _hash;
public:
    SparseTensorAddressRef()
        : _start(nullptr), _size(0u), _hash(0u)
    {
    }

    SparseTensorAddressRef(const void *start_in, size_t size_in)
        : _start(start_in), _size(size_in),
          _hash(calcHash())
    {
    }

    SparseTensorAddressRef(const SparseTensorAddressRef rhs, Stash &stash)
        : _start(nullptr),
          _size(rhs._size),
          _hash(rhs._hash)
    {
        void *res = stash.alloc(rhs._size);
        memcpy(res, rhs._start, rhs._size);
        _start = res;
    }

    size_t hash() const { return _hash; }

    size_t calcHash() const { return hashValue(_start, _size); }

    bool operator<(const SparseTensorAddressRef &rhs) const {
        size_t minSize = std::min(_size, rhs._size);
        int res = memcmp(_start, rhs._start, minSize);
        if (res != 0) {
            return res < 0;
        }
        return _size < rhs._size;
    }

    bool operator==(const SparseTensorAddressRef &rhs) const
    {
        if (_size != rhs._size || _hash != rhs._hash) {
            return false;
        }
        return memcmp(_start, rhs._start, _size) == 0;
    }

    const void *start() const { return _start; }
    size_t size() const { return _size; }
};

} // namespace vespalib::tensor
} // namespace vespalib
