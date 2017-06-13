// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "thread.h"

namespace vespalib {

__thread Thread *Thread::_currentThread = 0;

void
Thread::Proxy::Run(FastOS_ThreadInterface *, void *)
{
    assert(_currentThread == 0);
    _currentThread = &thread;
    start.await();
    if (!cancel) {
        started.countDown();
        runnable.run();
    }
    assert(_currentThread == &thread);
    _currentThread = 0;
}

Thread::Proxy::~Proxy() { }

Thread::Thread(Runnable &runnable)
    : _proxy(*this, runnable),
      _pool(STACK_SIZE, 1),
      _monitor(),
      _stopped(false),
      _woken(false)
{
    FastOS_ThreadInterface *thread = _pool.NewThread(&_proxy);
    assert(thread != 0);
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
    vespalib::MonitorGuard guard(_monitor);
    _stopped = true;
    guard.broadcast();
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
    vespalib::MonitorGuard guard(_monitor);
    if (!_stopped || _woken) {
        if (guard.wait((int)(s * 1000.0))) {
            _woken = _stopped;
        }
    } else {
        _woken = true;
    }
    return !_stopped;
}

Thread &
Thread::currentThread()
{
    Thread *thread = _currentThread;
    assert(thread != 0);
    return *thread;
}

void
Thread::sleep(size_t ms)
{
    FastOS_Thread::Sleep(ms);
}

} // namespace vespalib
