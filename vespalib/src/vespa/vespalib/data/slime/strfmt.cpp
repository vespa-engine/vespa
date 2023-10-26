// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "strfmt.h"
#include <stdarg.h>
#include <stdio.h>
#include <cassert>

namespace vespalib::slime {

std::string strfmt(const char *fmt, ...)
{
    va_list ap;
    std::string ret;
    int size = 100;
    do {
        ret.resize(size + 1);
        va_start(ap, fmt);
        size = vsnprintf(&ret[0], ret.size(), fmt, ap);
        va_end(ap);
    } while (size >= (int) ret.size());
    assert(size >= 0);
    ret.resize(size);
    return ret;
}

} // namespace vespalib::slime
