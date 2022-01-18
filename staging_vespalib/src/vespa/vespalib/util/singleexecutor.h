// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/threadexecutor.h>
#include <vespa/vespalib/util/thread.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/arrayqueue.hpp>
#include <vespa/vespalib/util/executor_idle_tracking.h>
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
    SingleExecutor(init_fun_t func, uint32_t reservedQueueSize);
    SingleExecutor(init_fun_t func, uint32_t reservedQueueSize, bool isQueueSizeHard, uint32_t watermark, duration reactionTime);
    ~SingleExecutor() override;
    Task::UP execute(Task::UP task) override;
    void setTaskLimit(uint32_t taskLimit) override;
    SingleExecutor & sync() override;
    void wakeup() override;
    size_t getNumThreads() const override;
    uint32_t getTaskLimit() const override { return _taskLimit.load(std::memory_order_relaxed); }
    uint32_t get_watermark() const { return _watermark.load(std::memory_order_relaxed); }
    duration get_reaction_time() const { return _reactionTime; }
    ExecutorStats getStats() override;
    SingleExecutor & shutdown() override;
    bool isBlocking() const { return !_overflow; }
private:
    using Lock = std::unique_lock<std::mutex>;
    void drain(Lock & lock);
    void run() override;
    void drain_tasks();
    void sleepProducer(Lock & guard, duration maxWaitTime, uint64_t wakeupAt);
    void run_tasks_till(uint64_t available);
    Task::UP wait_for_room_or_put_in_overflow_Q(Lock & guard, Task::UP task);
    uint64_t move_to_main_q(Lock & guard, Task::UP task);
    void move_overflow_to_main_q();
    void move_overflow_to_main_q(Lock & guard);
    uint64_t index(uint64_t counter) const {
        return counter & (_taskLimit.load(std::memory_order_relaxed) - 1);
    }

    uint64_t numTasks();
    uint64_t numTasks(Lock & guard) const {
        return num_tasks_in_main_q() + num_tasks_in_overflow_q(guard);
    }
    uint64_t num_tasks_in_overflow_q(Lock &) const {
        return _overflow ? _overflow->size() : 0;
    }
    uint64_t num_tasks_in_main_q() const {
        return _wp.load(std::memory_order_relaxed) - _rp.load(std::memory_order_acquire);
    }
    const double                _watermarkRatio;
    std::atomic<uint32_t>       _taskLimit;
    std::atomic<uint32_t>       _wantedTaskLimit;
    std::atomic<uint64_t>       _rp;
    std::unique_ptr<Task::UP[]> _tasks;
    std::mutex                  _mutex;
    std::condition_variable     _consumerCondition;
    std::condition_variable     _producerCondition;
    vespalib::Thread            _thread;
    ExecutorIdleTracker         _idleTracker;
    ThreadIdleTracker           _threadIdleTracker;
    uint64_t                    _wakeupCount;
    uint64_t                    _lastAccepted;
    ExecutorStats::QueueSizeT   _queueSize;
    std::atomic<uint64_t>       _wakeupConsumerAt;
    std::atomic<uint64_t>       _producerNeedWakeupAt;
    std::atomic<uint64_t>       _wp;
    std::atomic<uint32_t>       _watermark;
    const duration              _reactionTime;
    bool                        _closed;
    std::unique_ptr<ArrayQueue<Task::UP>> _overflow;
};

}
