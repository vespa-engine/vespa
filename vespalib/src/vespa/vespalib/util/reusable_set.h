// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "array.h"
#include "array.hpp"
#include <memory>
#include <cstring>


namespace vespalib {

class ReusableSet
{
public:
    using Mark = unsigned short;

    explicit ReusableSet(size_t size)
      : _array(size),
        _curval(-1),
        _sz(size)
    {
        clear();
    }

    ~ReusableSet() {
    }

    void clear() {
        if (++_curval == 0) {
            memset(bits(), 0, _sz * sizeof(Mark));
            ++_curval;
        }
    }

    void mark(size_t id) {
        _array[id] = _curval;
    }

    bool is_marked(size_t id) const {
        return (_array[id] == _curval);
    }

    Mark *bits() { return _array.begin(); }

    Mark generation() const { return _curval; }

    size_t memory_usage() const {
        return (_sz * sizeof(Mark)) + sizeof(ReusableSet);
    }

    size_t capacity() const { return _sz; }

private:
    Array<Mark> _array;
    Mark _curval;
    size_t _sz;
};

} // namespace
