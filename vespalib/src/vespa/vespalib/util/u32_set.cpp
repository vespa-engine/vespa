// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "u32_set.h"
#include <cassert>

namespace vespalib {

U32Set::U32Set(uint32_t initial_capacity)
    : _size(0),
      _capacity(std::min(std::max(roundUp2inN(initial_capacity), size_t{4u}), // Must never be < 4
                         size_t{dense_set_capacity_threshold()})),
      _buf(_capacity)
{
    // Dense capacity fixup
    if (_capacity == dense_set_capacity_threshold()) [[unlikely]] {
        _capacity = UINT32_MAX;
    }
}

U32Set::~U32Set() = default;

void U32Set::grow_and_rehash() {
    assert(_capacity <= dense_set_capacity_threshold());
    const size_t new_capacity = _capacity*2;
    if (new_capacity < dense_set_capacity_threshold()) [[likely]] {
        // Keeping it sparse
        BufferType new_buf(new_capacity);
        for (size_t i = 0; i < _capacity; ++i) {
            if (_buf[i] != 0) [[likely]] {
                insert_for_rehash(new_buf.data(), _buf[i], new_capacity);
            }
        }
        _buf = std::move(new_buf);
        _capacity = new_capacity;
    } else {
        // Packin' it in, packin' it up.
        BufferType new_buf(dense_bitvector_u32_elem_count());
        for (size_t i = 0; i < _capacity; ++i) {
            if (_buf[i] != 0) [[likely]] {
                // FIXME this will jump all over the place in memory... Pre-sort? vqsort?
                new_buf[_buf[i] / 32] |= (1u << (_buf[i] % 32));
            }
        }
        _buf = std::move(new_buf);
        _capacity = UINT32_MAX;
    }
}

}
