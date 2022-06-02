// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_runnable.h"
#include <vespa/vespalib/util/exceptions.h>
#include <cassert>

namespace document {

Runnable::Runnable()
    : _stateLock(),
      _stateCond(),
      _state(NOT_RUNNING)
{
}

Runnable::~Runnable() {
    std::lock_guard monitorGuard(_stateLock);
    assert(getState() == NOT_RUNNING);
}

bool Runnable::start(FastOS_ThreadPool& pool)
{
    std::unique_lock guard(_stateLock);
    _stateCond.wait(guard, [&](){ return (getState() != STOPPING);});

    if (getState() != NOT_RUNNING) return false;
    set_state(STARTING);
    if (pool.NewThread(this) == nullptr) {
        throw vespalib::IllegalStateException("Failed starting a new thread", VESPA_STRLOC);
    }
    return true;
}

void Runnable::set_state(State new_state) noexcept
{
    _state.store(new_state, std::memory_order_relaxed);
}

bool Runnable::stopping() const noexcept
{
    State s(getState());
    return (s == STOPPING) || (s == RUNNING && GetThread()->GetBreakFlag());
}

bool Runnable::running() const noexcept
{
    State s(getState());
    // Must check break-flag too, as threadpool will use that to close
    // down.
    return (s == STARTING || (s == RUNNING && !GetThread()->GetBreakFlag()));
}

bool Runnable::stop()
{
    std::lock_guard monitor(_stateLock);
    if (getState() == STOPPING || getState() == NOT_RUNNING) return false;
    GetThread()->SetBreakFlag();
    set_state(STOPPING);
    return onStop();
}

bool Runnable::onStop()
{
    return true;
}

bool Runnable::join() const
{
    std::unique_lock guard(_stateLock);
    assert ((getState() != STARTING) && (getState() != RUNNING));
    _stateCond.wait(guard, [&](){ return (getState() == NOT_RUNNING);});
    return true;
}

FastOS_ThreadId Runnable::native_thread_id() const noexcept
{
    return GetThread()->GetThreadId();
}

void Runnable::Run(FastOS_ThreadInterface*, void*)
{
    {
        std::lock_guard guard(_stateLock);
        // Don't set state if its already at stopping. (And let run() be
        // called even though about to stop for consistency)
        if (getState() == STARTING) {
            set_state(RUNNING);
        }
    }

    // By not catching exceptions, they should abort whole application.
    // We should thus not need to have a catch-all to set state to not
    // running.
    run();

    {
        std::lock_guard guard(_stateLock);
        set_state(NOT_RUNNING);
        _stateCond.notify_all();
    }
}

}
