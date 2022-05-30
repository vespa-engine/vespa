// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/util/time.h>
#include <mutex>
#include <vector>

class FNET_Transport;

namespace proton {

class TimerTask;

/**
 * ScheduledExecutor is a class capable of running Tasks at a regular
 * interval. The timer can be reset to clear all tasks currently being
 * scheduled.
 */
class ScheduledExecutor
{
private:
    using TaskList = std::vector<std::unique_ptr<TimerTask>>;
    using duration = vespalib::duration;
    using Executor = vespalib::Executor;
    FNET_Transport & _transport;
    std::mutex       _lock;
    TaskList         _taskList;

public:
    /**
     * Create a new timer, capable of scheduling tasks at fixed intervals.
     */
    ScheduledExecutor(FNET_Transport & transport);

    /**
     * Destroys this timer, finishing the current task executing and then
     * finishing.
     */
    ~ScheduledExecutor();

    /**
     * Schedule new task to be executed at specified intervals.
     *
     * @param task The task to schedule.
     * @param delay The delay to wait before first execution.
     * @param interval The interval in seconds.
     */
    void scheduleAtFixedRate(std::unique_ptr<Executor::Task> task, duration delay, duration interval);

    /**
     * Reset timer, clearing the list of task to execute.
     */
    void reset();
};

}

