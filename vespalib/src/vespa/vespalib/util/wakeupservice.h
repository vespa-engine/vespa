// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iwakeupservice.h"
#include "time.h"
#include <mutex>
#include <vector>
#include <thread>

namespace vespalib {

class WakeupService : public IWakeupService {
public:
    WakeupService(duration napTime);
    WakeupService(const WakeupService &) = delete;
    WakeupService & operator=(const WakeupService &) = delete;
    ~WakeupService() override;
    /**
     * Register the one to be woken up
     */
    std::shared_ptr<IDestructorCallback> registerForWakeup(IWakeup * toWakeup) override;
private:
    class Registration;
    void unregister(IWakeup * toWakeup);
    void runLoop();
    static void run(WakeupService *);
    duration                     _naptime;
    std::mutex                   _lock;
    bool                         _closed;
    std::vector<IWakeup *>       _toWakeup;
    std::unique_ptr<std::thread> _thread;
};

}
