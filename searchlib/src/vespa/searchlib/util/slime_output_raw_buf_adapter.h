// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/slime/output.h>
#include "rawbuf.h"

namespace search {

class SlimeOutputRawBufAdapter : public ::vespalib::slime::Output
{
private:
    RawBuf &_buf;

public:
    SlimeOutputRawBufAdapter(RawBuf &buf) : _buf(buf) {}
    virtual char *exchange(char *, size_t commit, size_t reserve) {
        _buf.Fill(commit);
        return _buf.GetWritableFillPos(reserve);
    }
};

} // namespace search

