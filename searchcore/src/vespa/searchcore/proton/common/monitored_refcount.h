// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <mutex>
#include <condition_variable>

namespace proton {

/*
 * Class containing a reference count that can be waited on to become zero.
 * Typically ancestor or member of a class that has to be careful of when
 * portions object can be properly torn down before destruction itself.
 */
class MonitoredRefCount
{
    std::mutex              _lock;
    std::condition_variable _cv;
    uint32_t                _refCount;

public:
    MonitoredRefCount();
    virtual ~MonitoredRefCount();
    void retain();
    void release();
    void waitForZeroRefCount();
};

} // namespace proton
