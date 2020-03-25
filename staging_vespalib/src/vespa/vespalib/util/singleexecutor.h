// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/threadexecutor.h>
#include <vespa/vespalib/util/thread.h>
#include <vespa/vespalib/util/time.h>
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
    void startSync();
    size_t getNumThreads() const override;
    uint32_t getTaskLimit() const { return _taskLimit.load(std::memory_order_relaxed); }
    Stats getStats() override;
private:
    using Lock = std::unique_lock<std::mutex>;
    uint64_t addTask(Task::UP task);
    void drain(Lock & lock);
    void run() override;
    void drain_tasks();
    void sleepProducer(Lock & guard, duration maxWaitTime, uint64_t wakeupAt);
    void run_tasks_till(uint64_t available);
    void wait_for_room(Lock & guard);
    uint64_t index(uint64_t counter) const {
        return counter & (_taskLimit.load(std::memory_order_relaxed) - 1);
    }

    uint64_t numTasks() const {
        return _wp.load(std::memory_order_relaxed) - _rp.load(std::memory_order_acquire);
    }
    std::atomic<uint32_t>       _taskLimit;
    std::atomic<uint32_t>       _wantedTaskLimit;
    std::atomic<uint64_t>       _rp;
    std::unique_ptr<Task::UP[]> _tasks;
    std::mutex                  _mutex;
    std::condition_variable     _consumerCondition;
    std::condition_variable     _producerCondition;
    vespalib::Thread            _thread;
    uint64_t                    _lastAccepted;
    Stats::QueueSizeT           _queueSize;
    std::atomic<uint64_t>       _wakeupConsumerAt;
    std::atomic<uint64_t>       _producerNeedWakeupAt;
    std::atomic<uint64_t>       _wp;
};

}
