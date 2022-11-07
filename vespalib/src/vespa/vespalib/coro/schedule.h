// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/executor.h>
#include <coroutine>
#include <exception>
#include <stdexcept>

namespace vespalib::coro {

struct ScheduleFailedException : std::runtime_error {
    using std::runtime_error::runtime_error;
};

// Schedule the current coroutine on the given executor. Throws an
// exception if the request was rejected by the executor.

auto schedule(Executor &executor) {
    struct [[nodiscard]] awaiter {
        Executor &executor;
        awaiter(Executor &executor_in)
            : executor(executor_in) {}
        bool await_ready() const noexcept { return false; }
        void await_suspend(std::coroutine_handle<> handle) {
            struct ResumeTask : Executor::Task {
                std::coroutine_handle<> handle;
                ResumeTask(std::coroutine_handle<> handle_in)
                  : handle(handle_in) {}
                void run() override { handle.resume(); }
            };
            Executor::Task::UP task = std::make_unique<ResumeTask>(handle);
            task = executor.execute(std::move(task));
            if (task) {
                throw ScheduleFailedException("rejected by executor");
            }
        }
        void await_resume() const noexcept {}
    };
    return awaiter(executor);
}

// Try to schedule the current coroutine on the given executor. If the
// awaiter returns true, the coroutine is now run by the executor. If
// the awaiter returns false, the request was rejected by the executor
// and the coroutine is still running in our original context.

auto try_schedule(Executor &executor) {
    struct [[nodiscard]] awaiter {
        Executor &executor;
        bool accepted;
        awaiter(Executor &executor_in)
            : executor(executor_in), accepted(true) {}
        bool await_ready() const noexcept { return false; }
        bool await_suspend(std::coroutine_handle<> handle) {
            struct ResumeTask : Executor::Task {
                std::coroutine_handle<> handle;
                ResumeTask(std::coroutine_handle<> handle_in)
                  : handle(handle_in) {}
                void run() override { handle.resume(); }
            };
            Executor::Task::UP task = std::make_unique<ResumeTask>(handle);
            task = executor.execute(std::move(task));
            if (task) {
                // need to start with accepted == true to avoid race
                // with handle.resume() from executor thread before
                // await_suspend has returned.
                accepted = false;
            }
            return accepted;
        }
        [[nodiscard]] bool await_resume() const noexcept { return accepted; }
    };
    return awaiter(executor);
}

}
