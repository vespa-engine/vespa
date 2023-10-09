// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "drainingbufferwriter.h"
#include <cassert>

namespace search {

DrainingBufferWriter::DrainingBufferWriter()
    : BufferWriter(),
      _buf(),
      _bytesWritten(0),
      _incompleteBuffers(0)
{
    _buf.resize(BUFFER_SIZE);
    setup(&_buf[0], _buf.size());
}


DrainingBufferWriter::~DrainingBufferWriter()
{
}


void
DrainingBufferWriter::flush() {
    // measure overhead above this flush method
    assert(_incompleteBuffers == 0); // all previous buffers must have been full
    size_t nowLen = usedLen();
    if (nowLen != _buf.size()) {
        // buffer is not full, only allowed for last buffer
        ++_incompleteBuffers;
    }
    if (nowLen == 0) {
        return; // empty buffer
    }
    _bytesWritten += nowLen;
    rewind();
}

} // namespace search
