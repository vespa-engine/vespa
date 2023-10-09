// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <sys/types.h>
#include <stdarg.h>
#include <cstdio>

#include "internal.h"

namespace ns_log {

void
throwInvalid(const char *format, ...)
{
    char buf[4000];
    va_list args;

    va_start(args, format);
    vsnprintf(buf, sizeof buf, format, args);
    va_end(args);

    throw InvalidLogException(buf);
}

} // end namespace ns_log
