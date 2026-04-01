// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "task.h"

#include "scheduler.h"

FNET_Task::FNET_Task(FNET_Scheduler* scheduler)
    : _task_scheduler(scheduler),
      _task_slot(0),
      _task_iter(0),
      _task_next(nullptr),
      _task_prev(nullptr),
      _killed(false) {}

FNET_Task::~FNET_Task() = default;

void FNET_Task::Schedule(double seconds) { _task_scheduler->Schedule(this, seconds); }

void FNET_Task::ScheduleNow() { _task_scheduler->ScheduleNow(this); }

void FNET_Task::Unschedule() { _task_scheduler->Unschedule(this); }

void FNET_Task::Kill() { _task_scheduler->Kill(this); }

void FNET_Task::PerformTask() {}
