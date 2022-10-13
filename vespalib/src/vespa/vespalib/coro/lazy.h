// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <concepts>
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
    struct promise_type {
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
        requires std::is_convertible_v<RET&&,T>
        void return_value(RET &&ret_value) noexcept(std::is_nothrow_constructible_v<T,RET&&>) {
            value = std::forward<RET>(ret_value);
        }
        void unhandled_exception() noexcept {
            exception = std::current_exception();
        }
        std::optional<T> value;
        std::exception_ptr exception;
        std::coroutine_handle<> waiter;
        promise_type(promise_type &&) = delete;
        promise_type(const promise_type &) = delete;
        promise_type() noexcept : value(std::nullopt), exception(), waiter(std::noop_coroutine()) {}
        T &result() & {
            if (exception) {
                std::rethrow_exception(exception);
            }
            return *value;
        }
        T &&result() && {
            if (exception) {
                std::rethrow_exception(exception);
            }
            return std::move(*value);
        }
    };
    using Handle = std::coroutine_handle<promise_type>;

private:
    Handle _handle;
    
    struct awaiter_base {
        Handle handle;
        awaiter_base(Handle handle_in) noexcept : handle(handle_in) {}
        bool await_ready() const noexcept { return handle.done(); }
        Handle await_suspend(std::coroutine_handle<> waiter) const noexcept {
            handle.promise().waiter = waiter;
            return handle;
        }
    };
    
public:
    Lazy(const Lazy &) = delete;
    Lazy &operator=(const Lazy &) = delete;
    explicit Lazy(Handle handle_in) noexcept : _handle(handle_in) {}
    Lazy(Lazy &&rhs) noexcept : _handle(std::exchange(rhs._handle, nullptr)) {}
    auto operator co_await() & noexcept {
        struct awaiter : awaiter_base {
            using awaiter_base::handle;
            awaiter(Handle handle_in) noexcept : awaiter_base(handle_in) {}
            decltype(auto) await_resume() const { return handle.promise().result(); }
        };
        return awaiter(_handle);
    }
    auto operator co_await() && noexcept {
        struct awaiter : awaiter_base {
            using awaiter_base::handle;
            awaiter(Handle handle_in) noexcept : awaiter_base(handle_in) {}
            decltype(auto) await_resume() const { return std::move(handle.promise()).result(); }
        };
        return awaiter(_handle);
    }
    ~Lazy() {
        if (_handle) {
            _handle.destroy();
        }
    }
};

}
