// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bufferwriter.h"
#include <vector>
#include <cstdint>

namespace search {

/**
 * Class to write to a "drain" buffer, used to measure performance of
 * BufferWriter and measure number of bytes written.
 */
class DrainingBufferWriter : public BufferWriter
{
    std::vector<char> _buf;
    size_t _bytesWritten;
    uint32_t _incompleteBuffers;
public:
    static constexpr size_t BUFFER_SIZE = 262144;

    DrainingBufferWriter();
    ~DrainingBufferWriter() override;
    void flush() override;
    size_t getBytesWritten() const { return _bytesWritten; }
};

} // namespace search
