// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "buffered_output.h"
#include <stdarg.h>
#include <stdio.h>
#include <assert.h>

namespace vespalib {
namespace slime {

void
BufferedOutput::printf(const char *fmt, ...)
{
    int space = 256;
    char *p = reserve(space);
    int size;
    va_list ap;
    va_start(ap, fmt);
    size = vsnprintf(p, space, fmt, ap);
    va_end(ap);
    assert(size >= 0);
    if (size >= space) {
        space = size + 1;
        p = reserve(space);
        va_start(ap, fmt);
        size = vsnprintf(p, space, fmt, ap);
        va_end(ap);
        assert((size + 1) == space);
    }
    commit(size);
}

} // namespace vespalib::slime
} // namespace vespalib
