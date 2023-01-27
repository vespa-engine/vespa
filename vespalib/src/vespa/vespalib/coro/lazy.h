// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "waiting_for.h"

#include <coroutine>
#include <optional>
#include <exception>
#include <utility>

namespace vespalib::coro {

/**
 * coroutine return type
 *
 * The coroutine is lazy (will suspend in initial_suspend) and
 * destroyed from the outside (will suspend in final_suspend). Waiting
 * for a Lazy<T> using co_await will use symmetric transfer to suspend
 * the waiting coroutine and resume this one. The waiting coroutine
 * is registered as a continuation and will be resumed again once the
 * result is available (also using symmetric transfer). The result is
 * assumed to be produced asynchronously. If you need to access it
 * from the outside (in that specific thread); use sync_wait.
 **/
template <std::movable T>
class [[nodiscard]] Lazy {
public:
    struct promise_type final : PromiseState<T> {
        using PromiseState<T>::result;
        Lazy<T> get_return_object() { return Lazy(Handle::from_promise(*this)); }
        static std::suspend_always initial_suspend() noexcept { return {}; }
        static auto final_suspend() noexcept {
            struct awaiter {
                bool await_ready() const noexcept { return false; }
                std::coroutine_handle<> await_suspend(Handle handle) const noexcept {
                    return handle.promise().waiter;
                }
                void await_resume() const noexcept {}
            };
            return awaiter();
        }
        template <typename RET>
        void return_value(RET &&ret_value) {
            result.set_value(std::forward<RET>(ret_value));
        }
        void unhandled_exception() noexcept {
            result.set_error(std::current_exception());
        }
        promise_type() noexcept : PromiseState<T>() {}
        ~promise_type();
    };
    using Handle = std::coroutine_handle<promise_type>;

private:
    Handle _handle;

    template <typename RET>
    struct WaitFor {
        Handle handle;
        WaitFor(Handle handle_in) noexcept : handle(handle_in) {}
        bool await_ready() const noexcept { return handle.done(); }
        Handle await_suspend(std::coroutine_handle<> waiter) const noexcept {
            handle.promise().waiter = waiter;
            return handle;
        }
        decltype(auto) await_resume() const { return RET::get(handle.promise()); }
    };
    struct LValue {
        static T& get(auto &&promise) { return promise.result.get_value(); }
    };
    struct RValue {
        static T&& get(auto &&promise) { return std::move(promise.result).get_value(); }
    };
    struct Result {
        static Received<T>&& get(auto &&promise) { return std::move(promise.result); }
    };

public:
    Lazy(const Lazy &) = delete;
    Lazy &operator=(const Lazy &) = delete;
    explicit Lazy(Handle handle_in) noexcept : _handle(handle_in) {}
    Lazy(Lazy &&rhs) noexcept : _handle(std::exchange(rhs._handle, nullptr)) {}
    auto operator co_await() & noexcept { return WaitFor<LValue>(_handle); }
    auto operator co_await() && noexcept { return WaitFor<RValue>(_handle); }
    auto forward() noexcept { return WaitFor<Result>(_handle); }
    ~Lazy() {
        if (_handle) {
            _handle.destroy();
        }
    }
};

template<std::movable T>
Lazy<T>::promise_type::~promise_type() = default;

// signal the completion of work without any result value
struct Done {};
using Work = Lazy<Done>;

}
