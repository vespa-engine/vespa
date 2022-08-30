// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "rawbuf.h"
#include <vespa/vespalib/data/output.h>

namespace search {

class SlimeOutputRawBufAdapter : public vespalib::Output
{
private:
    RawBuf &_buf;

public:
    SlimeOutputRawBufAdapter(RawBuf &buf) : _buf(buf) {}
    vespalib::WritableMemory reserve(size_t reserve) override {
        return vespalib::WritableMemory(_buf.GetWritableFillPos(reserve), reserve);
    }
    Output &commit(size_t commit) override {
        _buf.Fill(commit);
        return *this;
    }
};

} // namespace search

