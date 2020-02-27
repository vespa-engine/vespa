// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/util/executor_stats.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vector>
#include <mutex>

namespace search {

/**
 * Interface class to run multiple tasks in parallel, but tasks with same
 * id has to be run in sequence.
 */
class ISequencedTaskExecutor
{
public:
    class ExecutorId {
    public:
        ExecutorId() : ExecutorId(0) { }
        explicit ExecutorId(uint32_t id) : _id(id) { }
        uint32_t getId() const { return _id; }
        bool operator != (ExecutorId rhs) const { return _id != rhs._id; }
        bool operator == (ExecutorId rhs) const { return _id == rhs._id; }
        bool operator < (ExecutorId rhs) const { return _id < rhs._id; }
    private:
        uint32_t _id;
    };
    ISequencedTaskExecutor(uint32_t numExecutors);
    virtual ~ISequencedTaskExecutor();

    /**
     * Calculate which executor will handle an component.
     *
     * @param componentId   component id
     * @return              executor id
     */
    ExecutorId getExecutorId(uint64_t componentId) const;
    uint32_t getNumExecutors() const { return _numExecutors; }

    ExecutorId getExecutorId(vespalib::stringref componentId) const;

    /**
     * Schedule a task to run after all previously scheduled tasks with
     * same id.
     *
     * @param id     which internal executor to use
     * @param task   unique pointer to the task to be executed
     */
    virtual void executeTask(ExecutorId id, vespalib::Executor::Task::UP task) = 0;

    /**
     * Wrap lambda function into a task and schedule it to be run.
     * Caller must ensure that pointers and references are valid and
     * call sync before tearing down pointed to/referenced data.
      *
     * @param id        which internal executor to use
     * @param function  function to be wrapped in a task and later executed
     */
    template <class FunctionType>
    void executeLambda(ExecutorId id, FunctionType &&function) {
        executeTask(id, vespalib::makeLambdaTask(std::forward<FunctionType>(function)));
    }
    /**
     * Wait for all scheduled tasks to complete.
     */
    virtual void sync() = 0;

    virtual void setTaskLimit(uint32_t taskLimit) = 0;

    virtual vespalib::ExecutorStats getStats() = 0;

    /**
     * Wrap lambda function into a task and schedule it to be run.
     * Caller must ensure that pointers and references are valid and
     * call sync before tearing down pointed to/referenced data.
     *
     * @param componentId   component id
     * @param function      function to be wrapped in a task and later executed
     */
    template <class FunctionType>
    void execute(uint64_t componentId, FunctionType &&function) {
        ExecutorId id = getExecutorId(componentId);
        executeTask(id, vespalib::makeLambdaTask(std::forward<FunctionType>(function)));
    }

    /**
     * Wrap lambda function into a task and schedule it to be run.
     * Caller must ensure that pointers and references are valid and
     * call sync before tearing down pointed to/referenced data.
     *
     * @param id        executor id
     * @param function  function to be wrapped in a task and later executed
     */
    template <class FunctionType>
    void execute(ExecutorId id, FunctionType &&function) {
        executeTask(id, vespalib::makeLambdaTask(std::forward<FunctionType>(function)));
    }
    /**
     * For testing only
     */
    uint32_t getComponentHashSize() const { return _component2Id.size(); }
    uint32_t getComponentEffectiveHashSize() const { return _nextId; }
private:
    mutable std::vector<uint8_t> _component2Id;
    mutable std::mutex           _mutex;
    uint32_t                     _numExecutors;
    mutable uint32_t             _nextId;
};

} // namespace search
