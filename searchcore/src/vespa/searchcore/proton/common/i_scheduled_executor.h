// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <memory>

namespace proton {

/**
 * Interface used to run Tasks at a regular interval.
 */
class IScheduledExecutor {
public:
    using Handle = std::unique_ptr<vespalib::IDestructorCallback>;
    using duration = vespalib::duration;
    using Executor = vespalib::Executor;
    virtual ~IScheduledExecutor() = default;

    /**
     * Schedule a new task to be executed at specified intervals.
     *
     * @param task The task to schedule.
     * @param delay The delay to wait before first execution.
     * @param interval The interval between the task is executed.
     * @return A handle that will cancel the recurring task when it goes out of scope
     */
    [[nodiscard]] virtual Handle scheduleAtFixedRate(std::unique_ptr<Executor::Task> task, duration delay, duration interval) = 0;
};

}
