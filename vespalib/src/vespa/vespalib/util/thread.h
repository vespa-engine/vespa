// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "gate.h"
#include "runnable.h"
#include "active.h"
#include <atomic>
#include <thread>

namespace vespalib {

/**
 * Abstraction of the concept of running a single thread.
 **/
class Thread : public Active
{
private:
    using init_fun_t = Runnable::init_fun_t;
    static __thread Thread *_currentThread;

    Runnable               &_runnable;
    init_fun_t              _init_fun;
    vespalib::Gate          _start;
    std::mutex              _lock;
    std::condition_variable _cond;
    std::atomic<bool>       _stopped;
    bool                    _woken;
    std::jthread            _thread;

    void run();

public:
    Thread(Runnable &runnable, init_fun_t init_fun_in);
    ~Thread() override;
    void start() override;
    Thread &stop() override;
    void join() override;
    [[nodiscard]] bool stopped() const noexcept {
        return _stopped.load(std::memory_order_relaxed);
    }
    bool slumber(double s);
    static Thread &currentThread();
    static void sleep(size_t ms);
};

} // namespace vespalib
