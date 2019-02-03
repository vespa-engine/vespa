// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/xxhash/xxhash.h>
#include <cstring>

namespace vespalib::tensor {

/**
 * A reference to a compact sparse immutable address to a tensor cell.
 */
class SparseTensorAddressRef
{
    const void *_start;
    uint32_t    _size;
    uint32_t    _hash;
    static void * copy(const SparseTensorAddressRef rhs, Stash &stash) {
        void *res = stash.alloc(rhs._size);
        memcpy(res, rhs._start, rhs._size);
        return res;
    }
public:
    SparseTensorAddressRef()
        : _start(nullptr), _size(0u), _hash(0u)
    {
    }

    SparseTensorAddressRef(const void *start_in, uint32_t size_in)
        : _start(start_in), _size(size_in),
          _hash(calcHash())
    {
    }

    SparseTensorAddressRef(const SparseTensorAddressRef rhs, Stash &stash)
        : _start(copy(rhs, stash)),
          _size(rhs._size),
          _hash(rhs._hash)
    {}

    uint32_t hash() const { return _hash; }

    uint32_t calcHash() const { return XXH32(_start, _size, 0); }

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
    uint32_t size() const { return _size; }
};

}

