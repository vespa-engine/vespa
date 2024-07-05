// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stringfmt.h"
#include <memory>
#include <cassert>

namespace vespalib {

//-----------------------------------------------------------------------------

vespalib::string
make_string_va(const char *fmt, va_list ap)
{
    va_list ap2;
    int size = -1;

    char buffer[128];
    va_copy(ap2, ap);
    size = vsnprintf(buffer, sizeof(buffer), fmt, ap2);
    va_end(ap2);

    assert(size >= 0);
    if (sizeof(buffer) > static_cast<size_t>(size)) {
        return vespalib::string(buffer, size);
    }

    auto allocated = std::make_unique<char[]>(size + 1);
    va_copy(ap2, ap);
    int newLen = vsnprintf(allocated.get(), size + 1, fmt, ap2);
    va_end(ap2);
    assert(newLen == size);
    return vespalib::string(allocated.get(), size);
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
