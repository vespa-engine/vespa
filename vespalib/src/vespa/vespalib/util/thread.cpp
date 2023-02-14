// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "thread.h"
#include "time.h"
#include <thread>
#include <cassert>

namespace vespalib {

__thread Thread *Thread::_currentThread = nullptr;

void
Thread::run()
{
    assert(_currentThread == nullptr);
    _currentThread = this;
    _start.await();
    if (!stopped()) {
        _init_fun(_runnable);
    }
    assert(_currentThread == this);
    _currentThread = nullptr;
}

Thread::Thread(Runnable &runnable, init_fun_t init_fun_in)
  : _runnable(runnable),
    _init_fun(std::move(init_fun_in)),
    _start(),
    _lock(),
    _cond(),
    _stopped(false),
    _woken(false),
    _thread(&Thread::run, this)
{
}

Thread::~Thread()
{
    stop().start();
}

void
Thread::start()
{
    _start.countDown();
}

Thread &
Thread::stop()
{
    std::unique_lock guard(_lock);
    _stopped.store(true, std::memory_order_relaxed);
    _cond.notify_all();
    return *this;
}

void
Thread::join()
{
    if (_thread.joinable()) {
        _thread.join();
    }
}

bool
Thread::slumber(double s)
{
    std::unique_lock guard(_lock);
    if (!stopped() || _woken) {
        if (_cond.wait_for(guard, from_s(s)) == std::cv_status::no_timeout) {
            _woken = stopped();
        }
    } else {
        _woken = true;
    }
    return !stopped();
}

Thread &
Thread::currentThread()
{
    Thread *thread = _currentThread;
    assert(thread != nullptr);
    return *thread;
}

void
Thread::sleep(size_t ms)
{
    std::this_thread::sleep_for(std::chrono::milliseconds(ms));
}

} // namespace vespalib
