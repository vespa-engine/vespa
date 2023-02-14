// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "thread.h"

namespace vespalib {

Thread &
Thread::operator=(Thread &&rhs) noexcept
{
    // may call std::terminate
    _thread = std::move(rhs._thread);
    return *this;
}

void
Thread::join()
{
    if (_thread.joinable()) {
        _thread.join();
    }
}

Thread::~Thread()
{
    join();
}

Thread
Thread::start(Runnable &runnable, Runnable::init_fun_t init_fun)
{
    return start([&runnable, init_fun](){ init_fun(runnable); });
}

} // namespace vespalib
