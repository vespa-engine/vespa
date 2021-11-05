// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "vespa/vespalib/util/idestructorcallback.h"
#include <vespa/vespalib/util/isequencedtaskexecutor.h>

namespace search {

/**
 * Class that schedules a sequenced task when instance is
 * destroyed. Typically a shared pointer to an instance is passed
 * around to multiple worker threads that performs portions of a
 * larger task before dropping the shared pointer, triggering the
 * callback when all worker threads have completed.
 */
class ScheduleSequencedTaskCallback : public vespalib::IDestructorCallback
{
    vespalib::ISequencedTaskExecutor&            _executor;
    vespalib::ISequencedTaskExecutor::ExecutorId _id;
    std::unique_ptr<vespalib::Executor::Task>    _task;
public:
    ScheduleSequencedTaskCallback(vespalib::ISequencedTaskExecutor& executor,
                                  vespalib::ISequencedTaskExecutor::ExecutorId id,
                                  std::unique_ptr<vespalib::Executor::Task> task) noexcept;
    ~ScheduleSequencedTaskCallback() override;
};

} // namespace search
