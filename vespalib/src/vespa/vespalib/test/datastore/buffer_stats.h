// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cassert>
#include <cstddef>

namespace vespalib::datastore::test {

/*
 * Class representing expected buffer stats in unit tests.
 */
struct BufferStats
{
    // elements 
    size_t _used;
    size_t _hold;
    size_t _dead;
    // bytes
    size_t _extra_used;
    size_t _extra_hold;
    BufferStats() : _used(0), _hold(0), _dead(0), _extra_used(0), _extra_hold(0) {}
    BufferStats &used(size_t val) { _used += val; return *this; }
    BufferStats &hold(size_t val) { _hold += val; return *this; }
    BufferStats &dead(size_t val) { _dead += val; return *this; }
    BufferStats &extra_used(size_t val) { _extra_used += val; return *this; }
    BufferStats &extra_hold(size_t val) { _extra_hold += val; return *this; }
    
    BufferStats &hold_to_dead(size_t val) {
        dec_hold(val);
        _dead += val;
        return *this;
    }
    BufferStats &dec_used(size_t val) {
        assert(_used >= val);
        _used -= val;
        return *this;
    }
    BufferStats &dec_hold(size_t val) {
        assert(_hold >= val);
        _hold -= val;
        return *this;
    }
    BufferStats &dec_extra(size_t val) {
        assert(_extra_used >= val);
        assert(_extra_hold >= val);
        _extra_used -= val;
        _extra_hold -= val;
        return *this;
    }

};

}
