// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <coroutine>
#include <exception>

namespace vespalib::coro {

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
