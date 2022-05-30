// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/util/executor_stats.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vector>
#include <mutex>

namespace vespalib {

/**
 * Interface class to run multiple tasks in parallel, but tasks with same
 * id has to be run in sequence.
 */
class ISequencedTaskExecutor : public IWakeup
{
public:
    class ExecutorId {
    public:
        ExecutorId() noexcept : ExecutorId(0) { }
        explicit ExecutorId(uint32_t id) noexcept : _id(id) { }
        uint32_t getId() const noexcept { return _id; }
        bool operator != (ExecutorId rhs) const noexcept { return _id != rhs._id; }
        bool operator == (ExecutorId rhs) const noexcept { return _id == rhs._id; }
        bool operator < (ExecutorId rhs) const noexcept { return _id < rhs._id; }
    private:
        uint32_t _id;
    };
    using TaskList = std::vector<std::pair<ExecutorId, Executor::Task::UP>>;
    ISequencedTaskExecutor(uint32_t numExecutors);
    virtual ~ISequencedTaskExecutor();

    /**
     * Calculate which executor will handle an component.
     *
     * @param componentId   component id
     * @return              executor id
     */
    virtual ExecutorId getExecutorId(uint64_t componentId) const = 0;
    uint32_t getNumExecutors() const { return _numExecutors; }

    ExecutorId getExecutorIdFromName(stringref componentId) const;

    /**
     * Returns an executor id that is NOT equal to the given executor id,
     * using the given bias to offset the new id.
     *
     * This is relevant for pipelining operations on the same component,
     * by doing pipeline steps in different executors.
     */
    ExecutorId get_alternate_executor_id(ExecutorId id, uint32_t bias) const;

    /**
     * Schedule a task to run after all previously scheduled tasks with
     * same id.
     *
     * @param id     which internal executor to use
     * @param task   unique pointer to the task to be executed
     */
    virtual void executeTask(ExecutorId id, Executor::Task::UP task) = 0;
    /**
     * Schedule a list of tasks to run after all previously scheduled tasks with
     * same id. Default is to just iterate and execute one by one, but implementations
     * that can schedule all in one go more efficiently can implement this one.
     */
    virtual void executeTasks(TaskList tasks);
    /**
     * Call this one to ensure you get the attention of the workers.
     */
    void wakeup() override { }

    /**
     * Wrap lambda function into a task and schedule it to be run.
     * Caller must ensure that pointers and references are valid and
     * call sync_all before tearing down pointed to/referenced data.
      *
     * @param id        which internal executor to use
     * @param function  function to be wrapped in a task and later executed
     */
    template <class FunctionType>
    void executeLambda(ExecutorId id, FunctionType &&function) {
        executeTask(id, makeLambdaTask(std::forward<FunctionType>(function)));
    }
    /**
     * Wait for all scheduled tasks to complete.
     */
    virtual void sync_all() = 0;

    virtual void setTaskLimit(uint32_t taskLimit) = 0;

    virtual ExecutorStats getStats() = 0;

    /**
     * Wrap lambda function into a task and schedule it to be run.
     * Caller must ensure that pointers and references are valid and
     * call sync_all before tearing down pointed to/referenced data.
     *
     * @param componentId   component id
     * @param function      function to be wrapped in a task and later executed
     */
    template <class FunctionType>
    void execute(uint64_t componentId, FunctionType &&function) {
        ExecutorId id = getExecutorId(componentId);
        executeTask(id, makeLambdaTask(std::forward<FunctionType>(function)));
    }

    /**
     * Wrap lambda function into a task and schedule it to be run.
     * Caller must ensure that pointers and references are valid and
     * call sync_all before tearing down pointed to/referenced data.
     *
     * @param id        executor id
     * @param function  function to be wrapped in a task and later executed
     */
    template <class FunctionType>
    void execute(ExecutorId id, FunctionType &&function) {
        executeTask(id, makeLambdaTask(std::forward<FunctionType>(function)));
    }

private:
    uint32_t                     _numExecutors;
};

}
