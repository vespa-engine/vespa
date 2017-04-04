// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "timer.h"
#include <vespa/fnet/scheduler.h>
#include <vespa/fnet/task.h>
#include <vespa/fnet/transport.h>

namespace vespalib {

typedef vespalib::Executor::Task Task;

class TimerTask : public FNET_Task
{
private:
    TimerTask(const TimerTask &);
    TimerTask&operator=(const TimerTask &);

    FNET_Scheduler *_scheduler;
    Task::UP _task;
    double _interval;
public:
    TimerTask(FNET_Scheduler *scheduler, Task::UP task, double interval)
        : FNET_Task(scheduler),
          _task(std::move(task)),
          _interval(interval)
    { }

    ~TimerTask() {
        Kill();
    }

    void PerformTask() override {
        _task->run();
        Schedule(_interval);
    }
};

Timer::Timer()
    : _threadPool(128 * 1024),
      _transport(new FNET_Transport()),
      _lock(),
      _taskList()
{
    _transport->Start(&_threadPool);
}

Timer::~Timer()
{
    vespalib::LockGuard guard(_lock);
    _transport->ShutDown(true);
    _threadPool.Close();
    _taskList.clear();
}


void
Timer::scheduleAtFixedRate(vespalib::Executor::Task::UP task, double delay, double interval)
{
    vespalib::LockGuard guard(_lock);
    TimerTaskPtr tTask(new TimerTask(_transport->GetScheduler(), std::move(task), interval));
    _taskList.push_back(std::move(tTask));
    _taskList.back()->Schedule(delay);
}

void
Timer::reset()
{
    vespalib::LockGuard guard(_lock);
    _transport->ShutDown(true);
    _taskList.clear();
    _transport.reset(new FNET_Transport());
    _transport->Start(&_threadPool);
}

}
