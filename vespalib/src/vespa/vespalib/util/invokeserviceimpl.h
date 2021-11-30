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
    using VoidFunc = std::function<void()>;
public:
    InvokeServiceImpl(duration napTime);
    InvokeServiceImpl(const InvokeServiceImpl &) = delete;
    InvokeServiceImpl & operator=(const InvokeServiceImpl &) = delete;
    ~InvokeServiceImpl() override;
    std::unique_ptr<IDestructorCallback> registerInvoke(VoidFunc func) override;
private:
    class Registration;
    void unregister(VoidFunc func);
    void runLoop();
    duration                       _naptime;
    std::mutex                     _lock;
    bool                           _closed;
    std::vector<VoidFunc>          _toWakeup;
    std::unique_ptr<std::thread>   _thread;
};

}
