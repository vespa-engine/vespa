// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "input.h"
#include "output.h"
#include <vespa/vespalib/stllike/allocator.h>
#include <iosfwd>
#include <vector>

namespace vespalib {

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
    std::vector<char, allocator_large<char>> _data;
    size_t            _used;

public:
    SimpleBuffer();
    ~SimpleBuffer();
    Memory obtain() override;
    Input &evict(size_t bytes) override;
    WritableMemory reserve(size_t bytes) override;
    Output &commit(size_t bytes) override;
    SimpleBuffer &add(char c) {
        _data.push_back(c);
        ++_used;
        return *this;
    }
    Memory get() const { return Memory(_data.data(), _used); }
    bool operator==(const SimpleBuffer &rhs) const { return (get() == rhs.get()); }
};

std::ostream &operator<<(std::ostream &os, const SimpleBuffer &buf);

} // namespace vespalib
