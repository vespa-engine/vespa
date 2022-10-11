// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <concepts>
#include <coroutine>
#include <optional>

namespace vespalib::coro {

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
                    auto waiter = handle.promise().waiter;
                    return waiter ? waiter : std::noop_coroutine();
                }
                void await_resume() const noexcept {}
            };
            return awaiter();
        }
        void return_value(T ret_value) noexcept {
            value = std::move(ret_value);
        }
        static void unhandled_exception() { std::terminate(); }
        std::optional<T> value;
        std::coroutine_handle<> waiter;
        promise_type(promise_type &&) = delete;
        promise_type(const promise_type &) = delete;
        promise_type() : value(std::nullopt), waiter(nullptr) {}
    };
    using Handle = std::coroutine_handle<promise_type>;

private:
    Handle _handle;

public:
    Lazy(const Lazy &) = delete;
    Lazy &operator=(const Lazy &) = delete;
    explicit Lazy(Handle handle_in) noexcept : _handle(handle_in) {}
    Lazy(Lazy &&rhs) noexcept : _handle(std::exchange(rhs._handle, nullptr)) {}
    auto operator co_await() {
        struct awaiter {
            Handle handle;
            bool await_ready() const noexcept {
                return handle.done();
            }
            Handle await_suspend(std::coroutine_handle<> waiter) const noexcept {
                handle.promise().waiter = waiter;
                return handle;
            }
            T &await_resume() const noexcept {
                return *handle.promise().value;
            }
            awaiter(Handle handle_in) : handle(handle_in) {}
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
