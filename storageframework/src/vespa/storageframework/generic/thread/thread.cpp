// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/storageframework/generic/thread/thread.h>

#include <vespa/vespalib/util/sync.h>

namespace storage {
namespace framework {

void
Thread::interruptAndJoin(vespalib::Monitor* m)
{
    interrupt();
    if (m != 0) {
        vespalib::MonitorGuard monitorGuard(*m);
        monitorGuard.broadcast();
    }
    join();
}

} // framework
} // storage
