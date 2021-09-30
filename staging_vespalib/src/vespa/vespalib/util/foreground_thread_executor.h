// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/threadexecutor.h>
#include <atomic>

namespace vespalib {

/**
 * Implementation of the ThreadExecutor interface that runs all tasks in the foreground by the calling thread.
 */
class ForegroundThreadExecutor : public vespalib::ThreadExecutor {
private:
    std::atomic<size_t> _accepted;

public:
    ForegroundThreadExecutor() : _accepted(0) { }
    Task::UP execute(Task::UP task) override {
        task->run();
        ++_accepted;
        return Task::UP();
    }
    size_t getNumThreads() const override { return 0; }
    Stats getStats() override {
        return ExecutorStats(ExecutorStats::QueueSizeT(), _accepted.load(std::memory_order_relaxed), 0);
    }
    void setTaskLimit(uint32_t taskLimit) override { (void) taskLimit; }
    uint32_t getTaskLimit() const override { return std::numeric_limits<uint32_t>::max(); }
    void wakeup() override { }
};

}
