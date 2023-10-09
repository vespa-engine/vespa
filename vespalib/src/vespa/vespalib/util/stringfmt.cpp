// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stringfmt.h"
#include <cassert>

namespace vespalib {

//-----------------------------------------------------------------------------

vespalib::string make_string_va(const char *fmt, va_list ap)
{
    va_list ap2;
    vespalib::string ret;
    int size = -1;

    va_copy(ap2, ap);
    size = vsnprintf(ret.begin(), ret.capacity(), fmt, ap2);
    va_end(ap2);

    assert(size >= 0);
    if (ret.capacity() > static_cast<size_t>(size)) {
        // all OK
    } else {
        int newLen = size;
        ret.reserve(size+1);
        va_copy(ap2, ap);
        size = vsnprintf(ret.begin(), ret.capacity(), fmt, ap2);
        va_end(ap2);
        assert(newLen == size);
        (void)newLen;
    }
    ret.append_from_reserved(size);
    return ret;
}

/**
 * @brief construct string value printf style.
 *
 * You must \#include <vespa/vespalib/util/stringfmt.h>
 * to use this utility function.
 * @param fmt format string
 * @return formatted vespalib::string
 **/
vespalib::string make_string(const char *fmt, ...)
{
    va_list ap;
    va_start(ap, fmt);
    vespalib::string ret = make_string_va(fmt, ap);
    va_end(ap);
    return ret;
}

namespace make_string_short {
vespalib::string fmt(const char *format, ...)
{
    va_list ap;
    va_start(ap, format);
    vespalib::string ret = make_string_va(format, ap);
    va_end(ap);
    return ret;
}
} // namespace vespalib::make_string_short

} // namespace vespalib
