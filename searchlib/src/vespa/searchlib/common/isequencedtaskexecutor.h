// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/stllike/hash_fun.h>
#include "lambdatask.h"

namespace search
{

/**
 * Interface class to run multiple tasks in parallel, but tasks with same
 * id has to be run in sequence.
 */
class ISequencedTaskExecutor
{
public:
    virtual ~ISequencedTaskExecutor() { }

    /**
     * Schedule a task to run after all previously scheduled tasks with
     * same id.  All tasks must be scheduled from same thread.
     *
     * @param id         task id.
     * @param task       unique pointer to the task to be executed
     */
    virtual void executeTask(uint64_t id,
                             vespalib::Executor::Task::UP task) = 0;

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
     * @param id         task id.
     * @param function   function to be wrapped in a task and later executed
     */
    template <class FunctionType>
    inline void execute(uint64_t id, FunctionType &&function) {
        executeTask(id, makeLambdaTask(std::forward<FunctionType>(function)));
    }

    /**
     * Wrap lambda function into a task and schedule it to be run.
     * Caller must ensure that pointers and references are valid and
     * call sync before tearing down pointed to/referenced data.
     * All tasks must be scheduled from same thread.
     *
     * @param id         task id.
     * @param function   function to be wrapped in a task and later executed
     */
    template <class FunctionType>
    inline void execute(const vespalib::stringref id, FunctionType &&function) {
        vespalib::hash<vespalib::stringref> hashfun;
        executeTask(hashfun(id),
                    makeLambdaTask(std::forward<FunctionType>(function)));
    }
};

} // namespace search
