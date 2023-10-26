// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "input.h"
#include <vector>

struct LZ4F_dctx_s;

namespace vespalib {

/**
 * Input filter decompressing data stored in framed lz4 format.
 **/
class Lz4InputDecoder : public Input
{
private:
    Input            &_input;
    std::vector<char> _buffer;
    size_t            _used;
    size_t            _pos;
    bool              _eof;
    bool              _failed;
    vespalib::string  _reason;
    LZ4F_dctx_s      *_ctx;

    void fail(const char *reason);
    void decode_more();
public:
    Lz4InputDecoder(Input &input, size_t buffer_size);
    ~Lz4InputDecoder();
    Memory obtain() override;
    Input &evict(size_t bytes) override;
    bool failed() const { return _failed; }
    const vespalib::string &reason() const { return _reason; }
};

} // namespace vespalib
