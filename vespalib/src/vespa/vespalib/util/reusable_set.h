// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "array.h"
#include "array.hpp"
#include <memory>
#include <cstring>


namespace vespalib {

/**
 * Generational marker implementation of a vector of boolean values.
 * Limited API, used for marking "seen" nodes when exploring a graph.
 **/
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

    /**
     * Increments the generation value, only
     * initializing the underlying memory when it wraps
     **/
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
    size_t capacity() const { return _sz; }

    size_t memory_usage() const {
        return (_sz * sizeof(Mark)) + sizeof(ReusableSet);
    }

private:
    Array<Mark> _array;
    Mark _curval;
    size_t _sz;
};

} // namespace
