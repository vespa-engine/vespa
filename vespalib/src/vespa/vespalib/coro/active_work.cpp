// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "active_work.h"
#include <cassert>

namespace vespalib::coro {

bool
ActiveWork::join_awaiter::await_suspend(std::coroutine_handle<> handle) noexcept
{
    self._waiting = handle;
    return (self._pending.fetch_sub(1, std::memory_order_acq_rel) > 1);
}

ActiveWork::~ActiveWork()
{
    // NB: join must be called, even if there is no other work
    assert(_pending.load(std::memory_order_relaxed) == 0);
}

}
