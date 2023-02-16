// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "isequencedtaskexecutor.h"
#include "thread.h"
#include <vespa/vespalib/util/executor_idle_tracking.h>
#include <vespa/vespalib/util/arrayqueue.hpp>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/eventbarrier.hpp>
#include <mutex>
#include <condition_variable>
#include <optional>
#include <cassert>

namespace vespalib {

/**
 * Sequenced executor that balances the number of active threads in
 * order to optimize for throughput over latency by minimizing the
 * number of critical-path wakeups.
 **/
class AdaptiveSequencedExecutor : public ISequencedTaskExecutor
{
private:
    using Task = Executor::Task;

    struct TaggedTask {
        Task::UP task;
        uint32_t token;
        TaggedTask() : task(nullptr), token(0) {}
        TaggedTask(Task::UP task_in, uint32_t token_in)
            : task(std::move(task_in)), token(token_in) {}
        TaggedTask(TaggedTask &&rhs) = default;
        TaggedTask(const TaggedTask &rhs) = delete;
        TaggedTask &operator=(const TaggedTask &rhs) = delete;
        TaggedTask &operator=(TaggedTask &&rhs) {
            assert(task.get() == nullptr); // no overwrites
            task = std::move(rhs.task);
            token = rhs.token;
            return *this;
        }
        operator bool() const { return bool(task); }
    };

    /**
     * Values used to configure the executor.
     **/
    struct Config {
        size_t num_threads;
        size_t max_waiting;
        size_t max_pending;
        size_t wakeup_limit;
        bool is_max_pending_hard;
        void set_max_pending(size_t max_pending_in) {
            max_pending = std::max(1uL, max_pending_in);
            wakeup_limit = std::max(1uL, size_t(max_pending * 0.9));
            assert(wakeup_limit > 0);
            assert(wakeup_limit <= max_pending);
        }
        bool is_above_max_pending(size_t pending) {
            return (pending >= max_pending) && is_max_pending_hard;
        }
        Config(size_t num_threads_in, size_t max_waiting_in, size_t max_pending_in, bool is_max_pending_hard_in)
            : num_threads(num_threads_in),
              max_waiting(max_waiting_in),
              max_pending(1000),
              wakeup_limit(900),
              is_max_pending_hard(is_max_pending_hard_in)
        {
            assert(num_threads > 0);
            set_max_pending(max_pending_in);
        }
    };

    /**
     * Tasks that need to be sequenced are handled by a single strand.
     **/
    struct Strand {
        enum class State { IDLE, WAITING, ACTIVE };
        State state;
        ArrayQueue<TaggedTask> queue;
        Strand() noexcept;
        ~Strand();
    };

    /**
     * The state of a single worker thread.
     **/
    struct Worker {
        enum class State { RUNNING, BLOCKED, DONE };
        std::condition_variable cond;
        ThreadIdleTracker   idleTracker;
        State state;
        Strand *strand;
        Worker();
        ~Worker();
    };

    /**
     * State related to the executor itself.
     **/
    struct Self {
        enum class State { OPEN, BLOCKED, CLOSED };
        std::condition_variable cond;
        State state;
        size_t waiting_tasks;
        size_t pending_tasks;
        Self();
        ~Self();
    };

    /**
     * Stuff related to worker thread startup and shutdown.
     **/
    struct ThreadTools {
        AdaptiveSequencedExecutor &parent;
        ThreadPool pool;
        Gate allow_worker_exit;
        ThreadTools(AdaptiveSequencedExecutor &parent_in);
        ~ThreadTools();
        void start(size_t num_threads);
        void close();
    };

    struct BarrierCompletion {
        Gate gate;
        void completeBarrier() { gate.countDown(); }
    };

    std::unique_ptr<ThreadTools>       _thread_tools;
    mutable std::mutex                 _mutex;
    std::vector<Strand>                _strands;
    ArrayQueue<Strand*>                _wait_queue;
    ArrayQueue<Worker*>                _worker_stack;
    EventBarrier<BarrierCompletion>    _barrier;
    Self                               _self;
    ExecutorStats                      _stats;
    ExecutorIdleTracker                _idleTracker;
    Config                             _cfg;

    void maybe_block_self(std::unique_lock<std::mutex> &lock);
    void maybe_unblock_self(const std::unique_lock<std::mutex> &lock);

    void maybe_wake_worker(const std::unique_lock<std::mutex> &lock);
    bool obtain_strand(Worker &worker, std::unique_lock<std::mutex> &lock);
    bool exchange_strand(Worker &worker, std::unique_lock<std::mutex> &lock);
    TaggedTask next_task(Worker &worker, std::optional<uint32_t> prev_token);
    void worker_main();
public:
    AdaptiveSequencedExecutor(size_t num_strands, size_t num_threads,
                              size_t max_waiting, size_t max_pending,
                              bool is_max_pending_hard);
    ~AdaptiveSequencedExecutor() override;
    ExecutorId getExecutorId(uint64_t component) const override;
    void executeTask(ExecutorId id, Task::UP task) override;
    void sync_all() override;
    void setTaskLimit(uint32_t task_limit) override;
    ExecutorStats getStats() override;
    Config get_config() const;
};

}
