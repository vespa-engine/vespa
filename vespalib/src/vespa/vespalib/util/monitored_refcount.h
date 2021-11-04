// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <mutex>
#include <condition_variable>

namespace vespalib {

class RetainGuard;
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
    void retain() noexcept;
    void release() noexcept;
    friend RetainGuard;
public:
    MonitoredRefCount();
    virtual ~MonitoredRefCount();
    void waitForZeroRefCount();
    bool has_zero_ref_count();
};

}
