// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "received.h"
#include <coroutine>
#include <utility>

namespace vespalib::coro {

// State representing that someone (waiter) is waiting for something
// (result). This object cannot be moved or copied.
template <typename T>
struct PromiseState {
    Received<T> result;
    std::coroutine_handle<> waiter;
    PromiseState(const PromiseState &) = delete;
    PromiseState &operator=(const PromiseState &) = delete;
    PromiseState(PromiseState &&) = delete;
    PromiseState &operator=(PromiseState &&) = delete;
    PromiseState() noexcept : result(), waiter(std::noop_coroutine()) {}
    ~PromiseState();
};
template <typename T>
PromiseState<T>::~PromiseState() = default;

// A thin (smart) wrapper referencing a PromiseState<T> representing
// that a coroutine is waiting for a value. This class acts as a
// receiver in order to set the result value. When the owning
// reference is deleted, the waiting coroutine will be resumed.
template <typename T>
class WaitingFor {
private:
    PromiseState<T> *_state;
    WaitingFor(PromiseState<T> *state) noexcept : _state(state) {}
public:
    WaitingFor(WaitingFor &&rhs) noexcept : _state(std::exchange(rhs._state, nullptr)) {}
    WaitingFor(WaitingFor &rhs) = delete;
    WaitingFor &operator=(WaitingFor &rhs) = delete;
    ~WaitingFor();
    template <typename RET>
    void set_value(RET &&value) {
        _state->result.set_value(std::forward<RET>(value));
    }
    void set_error(std::exception_ptr exception) {
        _state->result.set_error(exception);
    }
    void set_done() {
        _state->result.set_done();
    }
    std::coroutine_handle<> release_waiter() {
        return std::exchange(_state->waiter, std::noop_coroutine());
    }
    void *release() {
        return std::exchange(_state, nullptr);
    }
    static WaitingFor from_pointer(void *ptr) {
        PromiseState<T> *state = reinterpret_cast<PromiseState<T>*>(ptr);
        return {state};
    }
    static WaitingFor from_state(PromiseState<T> &state) {
        return {&state};
    }
};

template <typename T>
WaitingFor<T>::~WaitingFor()
{
    if (_state != nullptr) {
        _state->waiter.resume();
    }
}

static_assert(receiver_of<WaitingFor<int>, int>);
static_assert(receiver_of<WaitingFor<std::unique_ptr<int>>, std::unique_ptr<int>>);

// Create a custom awaiter that will return a value of type T when the
// coroutine is resumed. The waiting coroutine will be represented as
// a WaitingFor<T> that is passed as the only parameter to 'f'. The
// return value of 'f' is returned from await_suspend, which means it
// must be void, bool or coroutine handle. If 'f' returns a value
// indicating that the coroutine should be resumed immediately,
// WaitingFor<T>::release_waiter() must be called to avoid resume
// being called as well. Note that await_ready will always return
// false, since the coroutine needs to be suspended in order to create
// the WaitingFor<T> object needed. Also, the WaitingFor<T> api
// implies that the value will be set from the outside and thus cannot
// be ready up-front. Also note that await_resume must return T by
// value, since the awaiter containing the result is a temporary
// object.
template <typename T, typename F>
auto awaiter_for(F &&f) {
    struct awaiter final : PromiseState<T> {
        using PromiseState<T>::result;
        using PromiseState<T>::waiter;
        std::decay_t<F> fun;
        awaiter(F &&f) : PromiseState<T>(), fun(std::forward<F>(f)) {}
        bool await_ready() const noexcept { return false; }
        T await_resume() { return std::move(result).get_value(); }
        decltype(auto) await_suspend(std::coroutine_handle<> handle) __attribute__((noinline)) {
            waiter = handle;
            return fun(WaitingFor<T>::from_state(*this));
        }
    };
    return awaiter(std::forward<F>(f));
}

}
