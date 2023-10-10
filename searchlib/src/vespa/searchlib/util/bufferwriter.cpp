// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bufferwriter.h"

namespace search {

BufferWriter::BufferWriter()
    : _cur(nullptr),
      _end(nullptr),
      _start(nullptr)
{
}

BufferWriter::~BufferWriter() = default;

void
BufferWriter::writeSlow(const void *src, size_t len)
{
    size_t residue = len;
    const char *csrc = static_cast<const char *>(src);
    for (;;) {
        size_t maxLen = freeLen();
        if (residue <= maxLen) {
            writeFast(csrc, residue);
            break;
        }
        if (maxLen != 0) {
            writeFast(csrc, maxLen);
            csrc += maxLen;
            residue -= maxLen;
        }
        flush();
    }
}

} // namespace search
