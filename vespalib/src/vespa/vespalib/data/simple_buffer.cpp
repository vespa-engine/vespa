// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_buffer.h"

#include <vespa/check_require.h>

namespace vespalib {

SimpleBuffer::~SimpleBuffer() = default;

Memory SimpleBuffer::obtain() {
    return {_data.data(), _used};
}

Input& SimpleBuffer::evict(size_t bytes) {
    CHECK(bytes <= _used);
    _data.erase(_data.begin(), _data.begin() + bytes);
    _used -= bytes;
    return *this;
}

WritableMemory SimpleBuffer::reserve(size_t bytes) {
    CHECK((_used + bytes) >= _used);
    _data.resize(_used + bytes, char(0x55));
    return {_data.data() + _used, bytes};
}

Output& SimpleBuffer::commit(size_t bytes) {
    CHECK(bytes <= (_data.size() - _used));
    _used += bytes;
    return *this;
}

std::ostream& operator<<(std::ostream& os, const SimpleBuffer& buf) {
    return os << buf.get();
}

} // namespace vespalib
