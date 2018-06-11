// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/stllike/hash_fun.h>
#include <vespa/vespalib/util/lambdatask.h>

namespace search {

/**
 * Interface class to run multiple tasks in parallel, but tasks with same
 * id has to be run in sequence.
 */
class ISequencedTaskExecutor
{
public:
    virtual ~ISequencedTaskExecutor() { }

    /**
     * Calculate which executor will handle an component. All callers
     * must be in the same thread.
     *
     * @param componentId   component id
     * @return              executor id
     */
    virtual uint32_t getExecutorId(uint64_t componentId) = 0;
    virtual uint32_t getNumExecutors() const = 0;

    uint32_t getExecutorId(vespalib::stringref componentId) {
        vespalib::hash<vespalib::stringref> hashfun;
        return getExecutorId(hashfun(componentId));
    }

    /**
     * Schedule a task to run after all previously scheduled tasks with
     * same id.
     *
     * @param executorId which internal executor to use
     * @param task       unique pointer to the task to be executed
     */
    virtual void executeTask(uint32_t exeucutorId, vespalib::Executor::Task::UP task) = 0;

    /**
     * Wrap lambda function into a task and schedule it to be run.
     * Caller must ensure that pointers and references are valid and
     * call sync before tearing down pointed to/referenced data.
      *
     * @param executorId    which internal executor to use
     * @param function      function to be wrapped in a task and later executed
     */
    template <class FunctionType>
    void executeLambda(uint32_t executorId, FunctionType &&function) {
        executeTask(executorId, vespalib::makeLambdaTask(std::forward<FunctionType>(function)));
    }
    /**
     * Wait for all scheduled tasks to complete.
     */
    virtual void sync() = 0;

    /**
     * Wrap lambda function into a task and schedule it to be run.
     * Caller must ensure that pointers and references are valid and
     * call sync before tearing down pointed to/referenced data.
     * All tasks must be scheduled from same thread.
     *
     * @param componentId   component id
     * @param function      function to be wrapped in a task and later executed
     */
    template <class FunctionType>
    void execute(uint64_t componentId, FunctionType &&function) {
        uint32_t executorId = getExecutorId(componentId);
        executeTask(executorId, vespalib::makeLambdaTask(std::forward<FunctionType>(function)));
    }

    /**
     * Wrap lambda function into a task and schedule it to be run.
     * Caller must ensure that pointers and references are valid and
     * call sync before tearing down pointed to/referenced data.
     * All tasks must be scheduled from same thread.
     *
     * @param componentId   component id
     * @param function      function to be wrapped in a task and later executed
     */
    template <class FunctionType>
    void execute(vespalib::stringref componentId, FunctionType &&function) {
        uint32_t executorId = getExecutorId(componentId);
        executeTask(executorId, vespalib::makeLambdaTask(std::forward<FunctionType>(function)));
    }
};

} // namespace search
