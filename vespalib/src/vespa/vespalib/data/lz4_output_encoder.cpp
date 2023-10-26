// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lz4_output_encoder.h"
#include <lz4frame.h>
#include <cassert>

namespace vespalib {

void
Lz4OutputEncoder::encode_frame()
{
    auto dst = _output.reserve(LZ4F_compressFrameBound(_used, nullptr));
    size_t written = LZ4F_compressFrame(dst.data, dst.size, &_buffer[0], _used, nullptr);
    assert(!LZ4F_isError(written));
    assert(written <= dst.size);
    _output.commit(written);
    _used = 0;
}

Lz4OutputEncoder::Lz4OutputEncoder(Output &output, size_t buffer_size)
    : _output(output),
      _buffer(buffer_size, 0),
      _used(0),
      _limit(buffer_size)
{
}

Lz4OutputEncoder::~Lz4OutputEncoder()
{
    if (_used > 0) {
        encode_frame();
    }
}

WritableMemory
Lz4OutputEncoder::reserve(size_t bytes)
{
    if ((_used + bytes) > _buffer.size()) {
        _buffer.resize(_used + bytes, 0);
    }
    return WritableMemory(&_buffer[_used], (_buffer.size() - _used));
}

Output &
Lz4OutputEncoder::commit(size_t bytes)
{
    _used += bytes;
    if (_used >= _limit) {
        encode_frame();
    }
    return *this;
}

} // namespace vespalib
