// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "schedule_sequenced_task_callback.h"

namespace search {

ScheduleSequencedTaskCallback::ScheduleSequencedTaskCallback(vespalib::ISequencedTaskExecutor& executor,
                                                             vespalib::ISequencedTaskExecutor::ExecutorId id,
                                                             std::unique_ptr<vespalib::Executor::Task> task) noexcept
    : _executor(executor),
      _id(id),
      _task(std::move(task))
{
}


ScheduleSequencedTaskCallback::~ScheduleSequencedTaskCallback()
{
    _executor.executeTask(_id, std::move(_task));
}

}
