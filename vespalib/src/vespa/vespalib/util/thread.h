// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "runnable.h"
#include <thread>
#include <concepts>

namespace vespalib {

/**
 * Thin thread abstraction that takes some things from std::thread
 * (not allowed to assign to a running thread), some things from
 * std::jthread (destructor does automatic join) and some things from
 * now deprecated thread pools (the join function can be called
 * multiple times and will only join the underlying thread if it is
 * joinable). Enables starting a thread either by using a runnable and
 * an init function or by forwarding directly to the std::thread
 * constructor. Note that this class does not handle cancellation.
 **/
class Thread
{
private:
    std::thread _thread;
    Thread(std::thread &&thread) noexcept : _thread(std::move(thread)) {}
public:
    Thread() noexcept : _thread() {}
    Thread(const Thread &rhs) = delete;
    Thread(Thread &&rhs) noexcept : Thread(std::move(rhs._thread)) {}
    std::thread::id get_id() const noexcept { return _thread.get_id(); }
    Thread &operator=(const Thread &rhs) = delete;
    Thread &operator=(Thread &&rhs) noexcept;
    void join();
    ~Thread();
    [[nodiscard]] static Thread start(Runnable &runnable, Runnable::init_fun_t init_fun_t);
    template<typename F, typename... Args>
    requires std::invocable<F,Args...>
    [[nodiscard]] static Thread start(F &&f, Args && ... args) {
        return Thread(std::thread(std::forward<F>(f), std::forward<Args>(args)...));
    };
};

} // namespace vespalib
