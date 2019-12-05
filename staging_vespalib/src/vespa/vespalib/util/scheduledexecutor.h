// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/fastos/thread.h>
#include <vector>

class FNET_Transport;

namespace vespalib {

class TimerTask;

/**
 * ScheduledExecutor is a class capable of running Tasks at a regular
 * interval. The timer can be reset to clear all tasks currently being
 * scheduled.
 */
class ScheduledExecutor
{
private:
    typedef std::unique_ptr<TimerTask> TimerTaskPtr;
    typedef std::vector<TimerTaskPtr> TaskList;
    FastOS_ThreadPool _threadPool;
    std::unique_ptr<FNET_Transport> _transport;
    vespalib::Lock _lock;
    TaskList _taskList;

public:
    /**
     * Create a new timer, capable of scheduling tasks at fixed intervals.
     */
    ScheduledExecutor();

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
    void scheduleAtFixedRate(vespalib::Executor::Task::UP task, double delay, double interval);

    /**
     * Reset timer, clearing the list of task to execute.
     */
    void reset();
};

}

