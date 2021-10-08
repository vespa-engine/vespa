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
    assert(_state == NOT_RUNNING);
}

bool Runnable::start(FastOS_ThreadPool& pool)
{
    std::unique_lock guard(_stateLock);
    _stateCond.wait(guard, [&](){ return (_state != STOPPING);});

    if (_state != NOT_RUNNING) return false;
    _state = STARTING;
    if (pool.NewThread(this) == nullptr) {
        throw vespalib::IllegalStateException("Failed starting a new thread", VESPA_STRLOC);
    }
    return true;
}

bool Runnable::stop()
{
    std::lock_guard monitor(_stateLock);
    if (_state == STOPPING || _state == NOT_RUNNING) return false;
    GetThread()->SetBreakFlag();
    _state = STOPPING;
    return onStop();
}

bool Runnable::onStop()
{
    return true;
}

bool Runnable::join() const
{
    std::unique_lock guard(_stateLock);
    assert ((_state != STARTING) && (_state != RUNNING));
    _stateCond.wait(guard, [&](){ return (_state == NOT_RUNNING);});
    return true;
}

void Runnable::Run(FastOS_ThreadInterface*, void*)
{
    {
        std::lock_guard guard(_stateLock);
        // Dont set state if its alreadyt at stopping. (And let run() be
        // called even though about to stop for consistency)
        if (_state == STARTING) {
            _state = RUNNING;
        }
    }

    // By not catching exceptions, they should abort whole application.
    // We should thus not need to have a catch all to set state to not
    // running.
    run();

    {
        std::lock_guard guard(_stateLock);
        _state = NOT_RUNNING;
        _stateCond.notify_all();
    }
}

}
