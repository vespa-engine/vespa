// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hdr_abort.h"
#include <cstdlib>
#include <cstdio>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib");

namespace vespalib {

void hdr_abort(const char *message,
               const char *file,
               unsigned int line)
{
    LOG(error, "%s:%d: Abort called. Reason: %s",
        file, line, message);
    fprintf(stderr, "%s:%d: Abort called. Reason: %s\n",
            file, line, message);
    abort();
}

} // namespace vespalib
