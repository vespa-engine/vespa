// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/threadexecutor.h>
#include <vespa/vespalib/util/thread.h>
#include <thread>
#include <atomic>

namespace vespalib {

/**
 * Has a single thread consuming tasks from a fixed size ringbuffer.
 * Made for throughput where the producer has no interaction with the consumer and
 * it is hence very cheap to produce a task. High and low watermark at 25%/75% is used
 * to reduce ping-pong.
 */
class SingleExecutor final : public vespalib::SyncableThreadExecutor, vespalib::Runnable {
public:
    explicit SingleExecutor(uint32_t taskLimit);
    ~SingleExecutor() override;
    Task::UP execute(Task::UP task) override;
    void setTaskLimit(uint32_t taskLimit) override;
    SingleExecutor & sync() override;
    size_t getNumThreads() const override;
    uint32_t getTaskLimit() const { return _taskLimit.load(std::memory_order_relaxed); }
    Stats getStats() override;
private:
    uint64_t addTask(Task::UP task);
    void run() override;
    void drain_tasks();
    void run_tasks_till(uint64_t available);
    void wait_for_room(MonitorGuard & guard);
    uint64_t index(uint64_t counter) const {
        return counter & (_taskLimit.load(std::memory_order_relaxed) - 1);
    }

    uint64_t numTasks() const {
        return _wp.load(std::memory_order_relaxed) - _rp.load(std::memory_order_relaxed);
    }
    std::atomic<uint32_t>       _taskLimit;
    std::atomic<uint32_t>       _wantedTaskLimit;
    std::atomic<uint64_t>       _rp;
    std::unique_ptr<Task::UP[]> _tasks;
    vespalib::Monitor           _consumerMonitor;
    vespalib::Monitor           _producerMonitor;
    vespalib::Thread            _thread;
    uint64_t                    _lastAccepted;
    std::atomic<uint64_t>       _maxPending;
    std::atomic<uint64_t>       _wakeupConsumerAt;
    std::atomic<bool>           _producerNeedWakeup;
    std::atomic<uint64_t>       _wp;
};

}
