// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <cstdarg>

#ifndef PRId64
  #define PRId64 "ld"
  #define PRIu64 "lu"
  #define PRIx64 "lx"
#endif

namespace vespalib {

extern vespalib::string make_string_va(const char *fmt, va_list ap);

extern vespalib::string make_string(const char *fmt, ...)
#ifdef __GNUC__
        // Add printf format checks with gcc
        __attribute__ ((format (printf,1,2)))
#endif
    ;

} // namespace vespalib

