// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <stdarg.h>
#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

extern vespalib::string make_vespa_string_va(const char *fmt, va_list ap);

extern vespalib::string make_vespa_string(const char *fmt, ...)
#ifdef __GNUC__
        // Add printf format checks with gcc
        __attribute__ ((format (printf,1,2)))
#endif
    ;

} // namespace vespalib

