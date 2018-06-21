// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/frameworkimpl/thread/appkiller.h>

#include <vespa/log/log.h>

LOG_SETUP(".deadlock.killer");

namespace storage {

void RealAppKiller::kill() {
    LOG(info, "Aborting the server to dump core, as we're "
              "most likely deadlocked and want a core file "
              "to view the stack traces.");
    LOG_ABORT("should not be reached");
}

} // storage
