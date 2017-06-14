// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "idestructorcallback.h"

namespace search
{

/**
 * Class that schedules a task when instance is destroyed. Typically a
 * shared pointer to an instance is passed around to multiple worker
 * threads that performs portions of a larger task before dropping the
 * shared pointer, triggering the callback when all worker threads
 * have completed.
 */
class ScheduleTaskCallback : public IDestructorCallback
{
    vespalib::Executor &_executor;
    vespalib::Executor::Task::UP _task;
public:
    ScheduleTaskCallback(vespalib::Executor &executor,
                         vespalib::Executor::Task::UP task)
        : _executor(executor),
          _task(std::move(task))
    {
    }
    virtual ~ScheduleTaskCallback() {
        _executor.execute(std::move(_task));
    }
};

} // namespace search
