// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "buffered_output.h"

namespace vbench {

BufferedOutput &
BufferedOutput::printf(const char *fmt, ...)
{
    ensureFree(256);
    int space = (_data.size - _pos);
    int size;
    va_list ap;
    va_start(ap, fmt);
    size = vsnprintf(_data.data + _pos, space, fmt, ap);
    va_end(ap);
    assert(size >= 0);
    if (size >= space) {
        space = size + 1;
        ensureFree(space);
        va_start(ap, fmt);
        size = vsnprintf(_data.data + _pos, space, fmt, ap);
        va_end(ap);
        assert((size + 1) == space);
    }
    _pos += size;
    return *this;
}

} // namespace vbench
