// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/util/comprfile.h>

namespace search::bitcompression { template <bool bigEndian> class FeatureDecodeContext; }

namespace search::diskindex::test {

template <bool bigEndian>
class CompressedWriteBuffer;

/*
 * This class contains a view of compressed data owned by the related CompressedWriteBuffer class.
 * It is used to test that encode + decode round trip reconstructs original values.
 */

template <bool bigEndian>
class CompressedReadBuffer {
    using DC = search::bitcompression::FeatureDecodeContext<bigEndian>;
    DC&                  _d;
    ComprFileReadContext _rc;
    uint32_t             _header_len;
    uint64_t             _file_bit_size;

public:
    CompressedReadBuffer(DC& d, const CompressedWriteBuffer<bigEndian>& wb);
    ~CompressedReadBuffer();
    void rewind(const CompressedWriteBuffer<bigEndian>& wb);
    const ComprFileReadContext& get_read_context() const noexcept { return _rc; }
};

}
