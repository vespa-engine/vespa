// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "shutdownguard.h"
#include <unistd.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.shutdownguard");

namespace vespalib {

namespace {
enum { STACK_SIZE = (1u << 16) };
}
void ShutdownGuard::Run(FastOS_ThreadInterface *, void *)
{
    while (_dieAtTime > steady_clock::now() && ! GetThread()->GetBreakFlag()) {
        std::this_thread::sleep_for(5ms);
    }
    if (_dieAtTime <= steady_clock::now()) {
        LOG(warning, "ShutdownGuard is now forcing an exit of the process.");
        _exit(EXIT_FAILURE);
    }
}

ShutdownGuard::ShutdownGuard(duration millis) :
    FastOS_Runnable(),
    _pool(STACK_SIZE, 1),
    _dieAtTime(steady_clock::now() + millis)
{
    _pool.NewThread(this);
}

ShutdownGuard::~ShutdownGuard()
{
    GetThread()->SetBreakFlag();
    GetThread()->Join();
    _pool.Close();
}

}
