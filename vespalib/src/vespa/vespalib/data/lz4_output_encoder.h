// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "output.h"
#include <vector>

namespace vespalib {

/**
 * Output filter compressing data into framed lz4 format. This class
 * will use the simple LZ4 compression API to encode complete frames
 * at a time, trading performance for simplicity.
 **/
class Lz4OutputEncoder : public Output
{
private:
    Output           &_output;
    std::vector<char> _buffer;
    size_t            _used;
    size_t            _limit;

    void encode_frame();
public:
    Lz4OutputEncoder(Output &output, size_t buffer_size);
    ~Lz4OutputEncoder();
    WritableMemory reserve(size_t bytes) override;
    Output &commit(size_t bytes) override;
};

} // namespace vespalib
