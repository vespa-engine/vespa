// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "buffer.h"

namespace vespalib::ws {

void
Buffer::ensure_free(size_t bytes)
{
    memmove(&_data[0], &_data[_read_pos], used());
    _write_pos -= _read_pos;
    _read_pos = 0;
    if (free() < bytes) {
        _data.resize(_write_pos + bytes);
    }
}

} // namespace vespalib::ws
