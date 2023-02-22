// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "thread.h"
#include "threadexecutor.h"
#include "eventbarrier.hpp"
#include "arrayqueue.hpp"
#include "gate.h"
#include "executor_idle_tracking.h"
#include <vector>
#include <functional>

namespace vespalib {

/**
 * An executor service that executes tasks in multiple threads.
 **/
class ThreadStackExecutorBase : public SyncableThreadExecutor,
                                public Runnable
{
public:
    using unique_lock = std::unique_lock<std::mutex>;

private:

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
    };

    struct Worker {
        std::mutex              lock;
        std::condition_variable cond;
        ThreadIdleTracker       idleTracker;
        uint32_t                pre_guard;
        bool                    idle;
        uint32_t                post_guard;
        TaggedTask              task;
        Worker();
        void verify(bool expect_idle) const;
    };

    struct BarrierCompletion {
        Gate gate;
        void completeBarrier() { gate.countDown(); }
    };

    struct BlockedThread {
        const uint32_t wait_task_count;
        mutable std::mutex lock;
        mutable std::condition_variable cond;
        bool blocked;
        BlockedThread(uint32_t wait_task_count_in)
            : wait_task_count(wait_task_count_in), lock(), cond(), blocked(true) {}
        void wait() const;
        void unblock();
    };

    ThreadPool                           _pool;
    mutable std::mutex                   _lock;
    std::condition_variable              _cond;
    ExecutorStats                        _stats;
    ExecutorIdleTracker                  _idleTracker;
    Gate                                 _executorCompletion;
    ArrayQueue<TaggedTask>               _tasks;
    ArrayQueue<Worker*>                  _workers;
    std::vector<BlockedThread*>          _blocked;
    EventBarrier<BarrierCompletion>      _barrier;
    uint32_t                             _taskCount;
    uint32_t                             _taskLimit;
    bool                                 _closed;
    init_fun_t                           _init_fun;
    static thread_local ThreadStackExecutorBase *_master;

    void block_thread(const unique_lock &, BlockedThread &blocked_thread);
    void unblock_threads(const unique_lock &);

    /**
     * Assign the given task to the given idle worker. This will wake
     * up a worker thread that is blocked in the obtainTask function.
     *
     * @param task the task to assign
     * @param worker an idle worker
     **/
    void assignTask(TaggedTask task, Worker &worker);

    /**
     * Obtain a new task to be run by the given worker.  This function
     * will block until a task is obtained or the executor is shut
     * down.
     *
     * @return true if a task was obtained, false if we are done
     * @param worker the worker looking for work
     **/
    bool obtainTask(Worker &worker);

    // Runnable (all workers live here)
    void run() override;

protected:
    /**
     * This will tell if a task will be accepted or not.
     * An implementation might decide to block.
     */
    virtual bool acceptNewTask(unique_lock & guard, std::condition_variable & cond) = 0;

    /**
     * If blocking implementation, this might wake up any waiters.
     *
     * @param monitor to use for signaling.
     */
    virtual void wakeup(unique_lock & guard, std::condition_variable & cond) = 0;

    /**
     *  Will tell you if the executor has been closed for new tasks.
     */
    bool closed() const { return _closed; }

    /**
     * This will cleanup before destruction. All implementations must call this
     * in destructor.
     */
    void cleanup();

    /**
     *  Will tell if there is room for a new task in the Q.
     */
    bool isRoomForNewTask() const { return (_taskCount < _taskLimit); }

    /**
     * Create a new thread stack executor. The task limit specifies
     * the maximum number of tasks that are currently handled by this
     * executor. Both the number of threads and the task limit must be
     * greater than 0.
     *
     * @param taskLimit upper limit on accepted tasks
     * @param init_fun custom function used to wrap the main loop of
     *                 each worker thread.
     **/
    ThreadStackExecutorBase(uint32_t taskLimit, init_fun_t init_fun);

    /**
     * This will start the theads. This is to avoid starting tasks in
     * constructor of base class.
     *
     * @param threads number of worker threads (concurrent tasks)
     */
    void start(uint32_t threads);

    /**
     * Returns true if the current thread is owned by this executor.
     **/
    bool owns_this_thread() const { return (_master == this); }

    /**
     * Sets a new upper limit for accepted number of tasks.
     */
    void internalSetTaskLimit(uint32_t taskLimit);

public:
    ThreadStackExecutorBase(const ThreadStackExecutorBase &) = delete;
    ThreadStackExecutorBase & operator = (const ThreadStackExecutorBase &) = delete;

    /**
     * Returns the number of idle workers. This is mostly useful for testing.
     **/
    size_t num_idle_workers() const;

    ExecutorStats getStats() override;

    Task::UP execute(Task::UP task) override;

    /**
     * Synchronize with this executor. This function will block until
     * all previously accepted tasks have been executed. This function
     * uses the event barrier algorithm (tm).
     *
     * @return this object; for chaining
     **/
    ThreadStackExecutorBase &sync() override;

    /**
     * Block the calling thread until the current task count is equal
     * to or lower than the given value.
     *
     * @param task_count target value to wait for
     **/
    void wait_for_task_count(uint32_t task_count);

    size_t getNumThreads() const override;
    void setTaskLimit(uint32_t taskLimit) override;
    uint32_t getTaskLimit() const override;

    void wakeup() override;

    /**
     * Shut down this executor. This will make this executor reject
     * all new tasks.
     *
     * @return this object; for chaining
     **/
    ThreadStackExecutorBase &shutdown() override;

    /**
     * Will invoke shutdown then sync.
     **/
    ~ThreadStackExecutorBase() override;
};

} // namespace vespalib
