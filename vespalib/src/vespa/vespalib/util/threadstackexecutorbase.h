// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "threadexecutor.h"
#include "eventbarrier.hpp"
#include "arrayqueue.hpp"
#include "sync.h"
#include "gate.h"
#include "runnable.h"
#include <memory>
#include <vector>
#include <functional>
#include "executor_stats.h"

class FastOS_ThreadPool;

namespace vespalib {

namespace thread { class ThreadInit; }

// Convenience macro used to create a function that can be used as an
// init function when creating an executor to inject a frame with the
// given name into the stack of all worker threads.

#define VESPA_THREAD_STACK_TAG(name) \
    int name(Runnable &worker) {     \
        worker.run();                \
        return 1;                    \
    }

/**
 * An executor service that executes tasks in multiple threads.
 **/
class ThreadStackExecutorBase : public ThreadExecutor,
                                public Runnable
{
public:
    /**
     * Internal stats that we want to observe externally. Note that
     * all stats are reset each time they are observed.
     **/
    using Stats = ExecutorStats;

    using init_fun_t = std::function<int(Runnable&)>;

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
        Monitor    monitor;
        uint32_t   pre_guard;
        bool       idle;
        uint32_t   post_guard;
        TaggedTask task;
        Worker() : monitor(), pre_guard(0xaaaaaaaa), idle(true), post_guard(0x55555555), task() {}
        void verify(bool expect_idle) {
            (void) expect_idle;
            assert(pre_guard == 0xaaaaaaaa);
            assert(post_guard == 0x55555555);
            assert(idle == expect_idle);
            assert(!task.task == expect_idle);
        }
    };

    struct BarrierCompletion {
        Gate gate;
        void completeBarrier() { gate.countDown(); }
    };

    struct BlockedThread {
        const uint32_t wait_task_count;
        Monitor monitor;
        bool blocked;
        BlockedThread(uint32_t wait_task_count_in)
            : wait_task_count(wait_task_count_in), monitor(), blocked(true) {}
        void wait() const;
        void unblock();
    };

    std::unique_ptr<FastOS_ThreadPool>   _pool;
    Monitor                              _monitor;
    Stats                                _stats;
    Gate                                 _executorCompletion;
    ArrayQueue<TaggedTask>               _tasks;
    ArrayQueue<Worker*>                  _workers;
    std::vector<BlockedThread*>          _blocked;
    EventBarrier<BarrierCompletion>      _barrier;
    uint32_t                             _taskCount;
    uint32_t                             _taskLimit;
    bool                                 _closed;
    std::unique_ptr<thread::ThreadInit>  _thread_init;

    void block_thread(const LockGuard &, BlockedThread &blocked_thread);
    void unblock_threads(const MonitorGuard &);

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
    virtual bool acceptNewTask(MonitorGuard & monitor) = 0;

    /**
     * If blocking implementation, this might wake up any waiters.
     *
     * @param monitor to use for signaling.
     */
    virtual void wakeup(MonitorGuard & monitor) = 0;

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
     * @param stackSize stack size per worker thread
     * @param taskLimit upper limit on accepted tasks
     * @param init_fun custom function used to wrap the main loop of
     *                 each worker thread.
     **/
    ThreadStackExecutorBase(uint32_t stackSize, uint32_t taskLimit,
                            init_fun_t init_fun);

    /**
     * This will start the theads. This is to avoid starting tasks in
     * constructor of base class.
     *
     * @param threads number of worker threads (concurrent tasks)
     */
    void start(uint32_t threads);

    /**
     * Sets a new upper limit for accepted number of tasks.
     */
    void internalSetTaskLimit(uint32_t taskLimit);

public:
    ThreadStackExecutorBase(const ThreadStackExecutorBase &) = delete;
    ThreadStackExecutorBase & operator = (const ThreadStackExecutorBase &) = delete;
    /**
     * Observe and reset stats for this object.
     *
     * @return stats
     **/
    Stats getStats();

    // inherited from Executor
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

    /**
     * Shut down this executor. This will make this executor reject
     * all new tasks.
     *
     * @return this object; for chaining
     **/
    ThreadStackExecutorBase &shutdown();

    /**
     * Will invoke shutdown then sync.
     **/
    ~ThreadStackExecutorBase();
};

} // namespace vespalib

