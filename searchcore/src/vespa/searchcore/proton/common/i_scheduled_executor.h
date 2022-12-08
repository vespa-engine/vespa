// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/util/time.h>
#include <memory>

namespace proton {

/**
 * Interface used to run Tasks at a regular interval.
 */
class IScheduledExecutor {
public:
    virtual ~IScheduledExecutor() = default;

    /**
     * Schedule a new task to be executed at specified intervals.
     *
     * @param task The task to schedule.
     * @param delay The delay to wait before first execution.
     * @param interval The interval between the task is executed.
     */
    virtual void scheduleAtFixedRate(std::unique_ptr<vespalib::Executor::Task> task,
                                     vespalib::duration delay, vespalib::duration interval) = 0;
};

}
