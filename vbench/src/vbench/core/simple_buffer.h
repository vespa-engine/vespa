// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "memory.h"
#include "input.h"
#include "output.h"
#include <ostream>

namespace vbench {

/**
 * Simple buffer class that implements the Input/Output
 * interfaces. Requesting the memory region of this buffer or
 * comparing buffers will only look at the data conceptually contained
 * in the buffer, ignoring evicted data and reserved data not yet
 * committed.
 **/
class SimpleBuffer : public Input,
                     public Output
{
private:
    std::vector<char> _data;
    size_t            _used;

public:
    SimpleBuffer();
    Memory get() const { return Memory(&_data[0], _used); }
    virtual Memory obtain();
    virtual Input &evict(size_t bytes);
    virtual WritableMemory reserve(size_t bytes);
    virtual Output &commit(size_t bytes);
    bool operator==(const SimpleBuffer &rhs) const;
};

std::ostream &operator<<(std::ostream &os, const SimpleBuffer &buf);

} // namespace vbench

