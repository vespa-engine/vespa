// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <stdint.h>
#include "blob.h"

namespace mbus {

/**
 * This class encapsulates a reference to a blob owned by someone
 * else. This object can be copied freely, but does not own
 * anything. This means that parameters of this class will typically
 * only be valid during the invocation of the current method.
 **/
class BlobRef
{
private:
    const char *_data; // default copy is ok
    uint32_t    _size;

public:
    /**
     * Create a new BlobRef referring to the given memory.
     *
     * @param d the actual data
     * @param s the size of the data in bytes
     **/
    BlobRef(const char *d, uint32_t s) : _data(d), _size(s) { }

    /**
     * Create a new BlobRef referring the memory owned by the given
     * Blob.
     *
     * @param b blob owning the data we want a reference to
     **/
    BlobRef(const Blob &b) :  _data(b.data()), _size(b.size()) { }

    /**
     * Obtain a pointer to the raw data referenced by this object.
     *
     * @return raw data pointer
     **/
    const char *data() const { return _data; }

    /**
     * Obtain the size of the data referenced by this object
     *
     * @return raw data size
     **/
    uint32_t size() const { return _size; }
};

} // namespace mbus

