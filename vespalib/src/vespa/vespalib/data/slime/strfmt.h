// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace vespalib::slime {

extern std::string strfmt(const char *fmt, ...)
#ifdef __GNUC__
        __attribute__ ((format (printf,1,2))) // Add printf format checks with gcc
        __attribute__((nonnull(1))) // Format string can never be null
#endif
    ;

} // namespace vespalib::slime
