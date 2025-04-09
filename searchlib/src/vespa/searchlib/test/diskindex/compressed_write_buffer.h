// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/util/comprfile.h>

namespace search::bitcompression { template <bool bigEndian> class FeatureEncodeContext; }

namespace search::diskindex::test {

/*
 * This class contains a memory buffer containing encoded features that is then made
 * available to a feature decoder via the related CompressedReadBuffer class. It is
 * used by unit tests to verify that encode + decode round trip reconstructs original values.
 */
template <bool bigEndian>
class CompressedWriteBuffer
{
    using EC = bitcompression::FeatureEncodeContext<bigEndian>;
    EC&                   _e;
    ComprFileWriteContext _wc;
    uint32_t              _header_len;     // Length of file header (bytes)
    uint64_t              _file_bit_size;

public:
    CompressedWriteBuffer(EC& e);
    ~CompressedWriteBuffer();
    void clear();
    void flush();
    void start_pad(uint32_t header_len); // Just pads without writing proper header
    uint32_t get_header_len() const noexcept { return _header_len; }
    uint64_t get_file_bit_size() const noexcept { return _file_bit_size; }
    const ComprFileWriteContext& get_write_context() const noexcept { return _wc; }
};

}
