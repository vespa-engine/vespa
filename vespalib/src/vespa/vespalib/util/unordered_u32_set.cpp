// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "unordered_u32_set.h"

#include "alloc.h"

#include <cassert>

namespace vespalib {

UnorderedU32Set::UnorderedU32Set() : UnorderedU32Set(16) {
}

UnorderedU32Set::UnorderedU32Set(uint32_t initial_capacity)
    : _size(0),
      // Default malloc allocation alignment is 16 bytes, so little point in having less capacity than this.
      // Adjust capacity up to ensure no resizes up to and including initial_capacity _insertions_.
      _capacity(std::max(roundUp2inN(initial_capacity + initial_capacity / 4), size_t{4u})),
      _buf(_capacity) {
}

UnorderedU32Set::~UnorderedU32Set() = default;

void UnorderedU32Set::grow_and_rehash() {
    const size_t new_capacity = _capacity * 2;
    BufferType   new_buf(new_capacity);
    for (size_t i = 0; i < _capacity; ++i) {
        if (_buf[i] != 0) [[likely]] {
            insert_for_rehash(new_buf.data(), _buf[i], new_capacity);
        }
    }
    _buf = std::move(new_buf);
    _capacity = new_capacity;
}

} // namespace vespalib
