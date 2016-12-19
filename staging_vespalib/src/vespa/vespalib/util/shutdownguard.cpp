// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "shutdownguard.h"

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.shutdownguard");

namespace vespalib {

void ShutdownGuard::Run(FastOS_ThreadInterface *, void *)
{
    while (_dieAtTime > getTimeInMillis()) {
        FastOS_Thread::Sleep(5);
    }
    if (_dieAtTime != 0) {
        LOG(warning, "ShutdownGuard is now forcing an exit of the process.");
        _exit(EXIT_FAILURE);
    }
}

ShutdownGuard::ShutdownGuard(uint64_t millis) :
    FastOS_Runnable(),
    _pool(STACK_SIZE, 1),
    _dieAtTime(getTimeInMillis() + millis)
{
    _pool.NewThread(this);
}

ShutdownGuard::~ShutdownGuard()
{
    _dieAtTime = 0;
    _pool.Close();
}


}
