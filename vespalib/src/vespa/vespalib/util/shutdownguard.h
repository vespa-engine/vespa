// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/time.h>
#include <thread>
#include <atomic>

namespace vespalib {

/**
 * Class to ensure that the current process finishes within a given time.
 * Construct with the number of milliseconds before triggering _exit();
 * destruct the ShutdownGuard object to dismiss the automatic process
 * termination.
 * A separate shutdown thread will perform the actual _exit() call.
 **/
class ShutdownGuard
{
    std::thread _thread;
    steady_time _dieAtTime;
    std::atomic<bool> _cancel;

    void run();

public:
    /**
     * Construct a shutdown guard with a given lifetime.
     * @arg millis the number of milliseconds before process automatically exits
     **/
    ShutdownGuard(duration millis);

    /**
     * Destructor that dismisses the guard and collects the shutdown thread.
     **/
    ~ShutdownGuard();

};

} // namespace vespalib

