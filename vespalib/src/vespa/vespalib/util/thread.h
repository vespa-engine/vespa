// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "gate.h"
#include "runnable.h"
#include "active.h"
#include <vespa/fastos/thread.h>

namespace vespalib {

/**
 * Abstraction of the concept of running a single thread.
 **/
class Thread : public Active
{
private:
    enum { STACK_SIZE = 256*1024 };
    static __thread Thread *_currentThread;

    struct Proxy : FastOS_Runnable {
        Thread         &thread;
        Runnable       &runnable;
        vespalib::Gate  start;
        vespalib::Gate  started;
        bool            cancel;

        Proxy(Thread &parent, Runnable &target);
        ~Proxy() override;

        void Run(FastOS_ThreadInterface *thisThread, void *arguments) override;
    };

    Proxy                   _proxy;
    FastOS_ThreadPool       _pool;
    std::mutex              _lock;
    std::condition_variable _cond;
    bool                    _stopped;
    bool                    _woken;

public:
    Thread(Runnable &runnable);
    ~Thread() override;
    void start() override;
    Thread &stop() override;
    void join() override;
    bool stopped() const { return _stopped; }
    bool slumber(double s);
    static Thread &currentThread();
    static void sleep(size_t ms);
};

} // namespace vespalib

