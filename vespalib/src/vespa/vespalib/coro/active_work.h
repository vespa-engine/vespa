// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "lazy.h"
#include "detached.h"
#include <coroutine>
#include <atomic>

namespace vespalib::coro {

// Tracks work that is being performed concurrently
class ActiveWork {
private:
    std::atomic<uint32_t> _pending;
    std::coroutine_handle<> _waiting;
    template <typename T>
    Detached signal_when_done(Lazy<T> &lazy) {
        co_await lazy.done();
        if (_pending.fetch_sub(1, std::memory_order_acq_rel) == 1) {
            _waiting.resume();
        }
    }
    struct join_awaiter {
        ActiveWork &self;
        join_awaiter(ActiveWork &self_in) noexcept : self(self_in) {}
        constexpr bool await_ready() const noexcept { return false; }
        constexpr void await_resume() const noexcept {}
        bool await_suspend(std::coroutine_handle<> handle) noexcept __attribute__((noinline));
    };
public:
    ActiveWork() : _pending(1), _waiting(std::noop_coroutine()) {}
    ~ActiveWork();
    template <typename T>
    void start(Lazy<T> &lazy) {
        _pending.fetch_add(1, std::memory_order_relaxed);
        signal_when_done(lazy);
    }
    auto join() noexcept { return join_awaiter(*this); }
};

}
