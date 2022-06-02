// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/frameworkimpl/thread/appkiller.h>

#include <vespa/log/log.h>

LOG_SETUP(".deadlock.killer");

namespace storage {

void RealAppKiller::kill() {
    LOG(error, "One or more threads have failed internal liveness checks; aborting process. "
               "A core dump will be generated (if enabled by the kernel). "
               "Please report this to the Vespa team at https://github.com/vespa-engine/vespa/issues");
    abort();
}

} // storage
