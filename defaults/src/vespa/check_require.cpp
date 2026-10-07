// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "check_require.h"

#include <cstdio>
#include <cstdlib>

namespace vespa::check {

int failed_requirement(const char* assertion, const char* file, int line) {
    fprintf(stderr, "%s:%d: Failed check: '%s'\n", file, line, assertion);
    abort();
}

} // namespace vespa::check
