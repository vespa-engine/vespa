// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

class FNET_Scheduler;

/**
 * This class represent a task that may be scheduled to be performed
 * by an instance of the @ref FNET_Scheduler class.
 **/
class FNET_Task {
    friend class FNET_Scheduler;

private:
    FNET_Scheduler* _task_scheduler;
    uint32_t        _task_slot;
    uint32_t        _task_iter;
    FNET_Task*      _task_next;
    FNET_Task*      _task_prev;
    bool            _killed;

public:
    FNET_Task(const FNET_Task&) = delete;
    FNET_Task& operator=(const FNET_Task&) = delete;

    /**
     * Construct a task that may be scheduled by the given scheduler.
     *
     * @param scheduler the scheduler that will be used to schedule this
     *                  task.
     **/
    FNET_Task(FNET_Scheduler* scheduler);
    virtual ~FNET_Task();

    /**
     * Schedule this task to be performed in the given amount of
     * seconds.
     *
     * @param seconds the number of seconds until this task
     *                should be performed.
     **/
    void Schedule(double seconds);

    /**
     * Schedule this task to be performed as soon as possible.
     **/
    void ScheduleNow();

    /**
     * Unschedule this task. If the scheduler is currently performing
     * this task, this method will block until the task is
     * completed.
     **/
    void Unschedule();

    /**
     * This method does the same as the @ref Unschedule method, but also
     * makes sure that this task may not be scheduled in the future.
     **/
    void Kill();

    /**
     * This method will be invoked by the scheduler to perform this
     * task. Note that since the scheduling is one-shot, it is legal for
     * a task to re-schedule itself in this method.
     **/
    virtual void PerformTask();
};
