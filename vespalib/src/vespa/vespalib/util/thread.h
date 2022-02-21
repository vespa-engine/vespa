// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "gate.h"
#include "runnable.h"
#include "active.h"
#include <vespa/fastos/thread.h>
#include <atomic>

namespace vespalib {

/**
 * Abstraction of the concept of running a single thread.
 **/
class Thread : public Active
{
private:
    using init_fun_t = Runnable::init_fun_t;
    enum { STACK_SIZE = 256*1024 };
    static __thread Thread *_currentThread;

    struct Proxy : FastOS_Runnable {
        Thread         &thread;
        Runnable       &runnable;
        init_fun_t      init_fun;
        vespalib::Gate  start;
        vespalib::Gate  started;
        bool            cancel;

        Proxy(Thread &parent, Runnable &target, init_fun_t init_fun_in);
        ~Proxy() override;

        void Run(FastOS_ThreadInterface *thisThread, void *arguments) override;
    };

    Proxy                   _proxy;
    FastOS_ThreadPool       _pool;
    std::mutex              _lock;
    std::condition_variable _cond;
    std::atomic<bool>       _stopped;
    bool                    _woken;

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

