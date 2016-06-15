// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "output.h"
#include "memory.h"
#include <vector>
#include <assert.h>

namespace vespalib {
namespace slime {

/**
 * Simple output buffer implementation.
 **/
class SimpleBuffer : public Output
{
private:
    std::vector<char> _data;
    size_t            _used;

public:
    SimpleBuffer() : _data(), _used(0) {}
    virtual char *exchange(char *p, size_t commit, size_t reserve) {
        (void) p;
        assert(p == &_data[_used] || commit == 0);
        assert((_data.size() - _used) >= commit);
        _used += commit;
        _data.resize(_used + reserve, char(0x55));
        return &_data[_used];
    }
    SimpleBuffer &add(char byte) {
        assert(_data.size() == _used);
        _data.push_back(byte);
        ++_used;
        return *this;
    }
    Memory get() const {
        return Memory(&_data[0], _used);
    }
};

} // namespace vespalib::slime
} // namespace vespalib

