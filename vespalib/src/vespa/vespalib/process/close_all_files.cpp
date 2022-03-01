// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "close_all_files.h"
#include <unistd.h>

namespace vespalib {

// this is what we want to do, when possible:
// 
// close_range(3, ~0U, CLOSE_RANGE_UNSHARE);

void close_all_files() {
    int fd_limit = sysconf(_SC_OPEN_MAX);
    for (int fd = STDERR_FILENO + 1; fd < fd_limit; ++fd) {
        close(fd);
    }
}

} // namespace vespalib
