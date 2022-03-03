// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "invokeservice.h"
#include "time.h"
#include <mutex>
#include <vector>
#include <thread>

namespace vespalib {

/**
 * An invoke service what will invoke the given function with at specified frequency.
 */
class InvokeServiceImpl : public InvokeService {
public:
    InvokeServiceImpl(duration napTime);
    InvokeServiceImpl(const InvokeServiceImpl &) = delete;
    InvokeServiceImpl & operator=(const InvokeServiceImpl &) = delete;
    ~InvokeServiceImpl() override;
    std::unique_ptr<IDestructorCallback> registerInvoke(InvokeFunc func) override;
    const std::atomic<steady_time> & nowRef() const { return _now; }
private:
    using IdAndFunc = std::pair<uint64_t, InvokeFunc>;
    class Registration;
    void unregister(uint64_t id);
    void runLoop();
    duration                       _naptime;
    std::atomic<steady_time>       _now;
    std::mutex                     _lock;
    uint64_t                       _currId;
    bool                           _closed;
    std::vector<IdAndFunc>         _toInvoke;
    std::unique_ptr<std::thread>   _thread;
};

}
