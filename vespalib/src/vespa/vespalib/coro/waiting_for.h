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
    WaitingFor() noexcept : _state(nullptr) {}
    WaitingFor(WaitingFor &&rhs) noexcept : _state(std::exchange(rhs._state, nullptr)) {}
    WaitingFor &operator=(WaitingFor &&rhs) {
        if (_state) {
            _state->result.set_done(); // canceled
            _state->waiter.resume();
        }
        _state = std::exchange(rhs._state, nullptr);
        return *this;
    }
    WaitingFor(const WaitingFor &rhs) = delete;
    WaitingFor &operator=(const WaitingFor &rhs) = delete;
    ~WaitingFor();
    operator bool() const noexcept { return _state; }
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
    // Unasking the question. This will drop the internal reference to
    // the promise state and return the handle for the waiting
    // coroutine. A function responsible for starting an async
    // operation may chose to do 'wf.set_value(<result>)' followed by
    // 'return wf.mu()' to convert the async operation to a sync
    // operation by immediately resuming the waiting coroutine (by
    // symmetrically transferring control to itself).
    [[nodiscard]] std::coroutine_handle<> mu() {
        auto handle = std::exchange(_state->waiter, std::noop_coroutine());
        _state = nullptr;
        return handle;
    }
    // If some branch in the async start function wants to return mu,
    // other branches can return nop. This is to help the compiler
    // figure out the return type of lambdas, since
    // std::noop_coroutine() is a distinct type.
    [[nodiscard]] static std::coroutine_handle<> nop() noexcept {
        return std::noop_coroutine();
    }
};

template <typename T>
WaitingFor<T>::~WaitingFor()
{
    if (_state) {
        _state->waiter.resume();
    }
}

static_assert(receiver_of<WaitingFor<int>, int>);
static_assert(receiver_of<WaitingFor<std::unique_ptr<int>>, std::unique_ptr<int>>);

// concept indicating that F is a function that starts an async
// operation with T as result. The result will eventually be delivered
// to the WaitingFor<T> passed to the function. This function has
// optional support for symmetric transfer to switch to another
// coroutine as a side-effect of starting the async operation. This
// also enables the function to change the operation form async to
// sync by setting the value directly in the function and returning
// wf.mu()
template <typename F, typename T>
concept start_async_op = requires(F &&f) { std::decay_t<F>(std::forward<F>(f)); } &&
(requires(std::decay_t<F> cpy, WaitingFor<T> wf) { { cpy(std::move(wf)) } -> std::same_as<void>; } ||
 requires(std::decay_t<F> cpy, WaitingFor<T> wf) { { cpy(std::move(wf)) } -> std::same_as<std::coroutine_handle<>>; });

// Create a custom awaiter that will return a value of type T when the
// coroutine is resumed. The operation waited upon is represented by
// the function object used to start it. Note that await_ready will
// always return false, since the coroutine needs to be suspended in
// order to create the WaitingFor<T> object needed. Also, the
// WaitingFor<T> api implies that the value will be set from the
// outside and thus cannot be ready up-front. Also note that
// await_resume must return T by value, since the awaiter containing
// the result is a temporary object.
template <typename T, typename F>
requires start_async_op<F,T>
auto wait_for(F &&on_suspend) {
    struct awaiter final : PromiseState<T> {
        using PromiseState<T>::result;
        using PromiseState<T>::waiter;
        std::decay_t<F> on_suspend;
        awaiter(F &&on_suspend_in) : PromiseState<T>(), on_suspend(std::forward<F>(on_suspend_in)) {}
        bool await_ready() const noexcept { return false; }
        T await_resume() { return std::move(result).get_value(); }
        decltype(auto) await_suspend(std::coroutine_handle<> handle) __attribute__((noinline)) {
            waiter = handle;
            return on_suspend(WaitingFor<T>::from_state(*this));
        }
    };
    return awaiter(std::forward<F>(on_suspend));
}

}
