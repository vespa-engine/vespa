// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "shutdownguard.h"
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.shutdownguard");

namespace vespalib {

void
ShutdownGuard::run()
{
    while (_dieAtTime > steady_clock::now() && !_cancel.load(std::memory_order_relaxed)) {
        std::this_thread::sleep_for(5ms);
    }
    if (_dieAtTime <= steady_clock::now()) {
        LOG(warning, "ShutdownGuard is now forcing an exit of the process.");
        _exit(EXIT_FAILURE);
    }
}

ShutdownGuard::ShutdownGuard(duration millis)
  : _thread(),
    _dieAtTime(steady_clock::now() + millis)
{
    _thread = std::thread(&ShutdownGuard::run, this);
}

ShutdownGuard::~ShutdownGuard()
{
    _cancel = true;
    _thread.join();
}

}
