// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_scheduled_executor.h"
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
class ScheduledExecutor : public IScheduledExecutor
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
    ~ScheduledExecutor() override;

    void scheduleAtFixedRate(std::unique_ptr<Executor::Task> task, duration delay, duration interval) override;

    /**
     * Reset timer, clearing the list of task to execute.
     */
    void reset();
};

}

