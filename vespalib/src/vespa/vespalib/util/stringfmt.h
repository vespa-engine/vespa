// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <cstdarg>
#include <cinttypes>

namespace vespalib {

extern vespalib::string make_string_va(const char *fmt, va_list ap);

extern vespalib::string make_string(const char *fmt, ...) __attribute__ ((format (printf,1,2)));

namespace make_string_short {
extern vespalib::string fmt(const char *format, ...) __attribute__ ((format (printf,1,2)));
} // namespace vespalib::make_string_short

} // namespace vespalib
