// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "thread.h"
#include "time.h"
#include <thread>
#include <cassert>

namespace vespalib {

__thread Thread *Thread::_currentThread = nullptr;

Thread::Proxy::Proxy(Thread &parent, Runnable &target, init_fun_t init_fun_in)
    : thread(parent), runnable(target), init_fun(std::move(init_fun_in)),
      start(), started(), cancel(false)
{ }

void
Thread::Proxy::Run(FastOS_ThreadInterface *, void *)
{
    assert(_currentThread == nullptr);
    _currentThread = &thread;
    start.await();
    if (!cancel) {
        started.countDown();
        init_fun(runnable);
    }
    assert(_currentThread == &thread);
    _currentThread = nullptr;
}

Thread::Proxy::~Proxy() = default;

Thread::Thread(Runnable &runnable, init_fun_t init_fun_in)
    : _proxy(*this, runnable, std::move(init_fun_in)),
      _pool(STACK_SIZE, 1),
      _lock(),
      _cond(),
      _stopped(false),
      _woken(false)
{
    FastOS_ThreadInterface *thread = _pool.NewThread(&_proxy);
    assert(thread != nullptr);
    (void)thread;
}

Thread::~Thread()
{
    _proxy.cancel = true;
    _proxy.start.countDown();
}

void
Thread::start()
{
    _proxy.start.countDown();
    _proxy.started.await();
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
    _pool.Close();
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
