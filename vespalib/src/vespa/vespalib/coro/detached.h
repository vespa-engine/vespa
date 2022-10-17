// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <coroutine>
#include <exception>

namespace vespalib::coro {

/**
 * coroutine return type
 *
 * The coroutine is eager (will not suspend in initial_suspend) and
 * self destroying (will not suspend in final_suspend). The return
 * value gives no way of interacting with the coroutine. Without any
 * co_await operations this acts similar to a normal subroutine. Note
 * that letting a detached coroutine wait for a Lazy<T> will
 * essentially attach it to the Lazy<T> as a continuation and resume
 * it, but will require the Lazy<T> not to be deleted mid flight
 * (started but not completed).
 **/
struct Detached {
    struct promise_type {
        Detached get_return_object() { return {}; }
        static std::suspend_never initial_suspend() noexcept { return {}; }
        static std::suspend_never final_suspend() noexcept { return {}; }
        static void unhandled_exception() { std::terminate(); }
        void return_void() noexcept {};
    };
};

}
