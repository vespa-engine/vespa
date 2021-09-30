// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "scheduledexecutor.h"
#include <vespa/fnet/scheduler.h>
#include <vespa/fnet/task.h>
#include <vespa/fnet/transport.h>
#include <vespa/vespalib/util/size_literals.h>

namespace vespalib {

typedef vespalib::Executor::Task Task;

class TimerTask : public FNET_Task
{
private:
    TimerTask(const TimerTask &);
    TimerTask&operator=(const TimerTask &);

    FNET_Scheduler *_scheduler;
    Task::UP _task;
    duration _interval;
public:
    TimerTask(FNET_Scheduler *scheduler, Task::UP task, duration interval)
        : FNET_Task(scheduler),
          _task(std::move(task)),
          _interval(interval)
    { }

    ~TimerTask() {
        Kill();
    }

    void PerformTask() override {
        _task->run();
        Schedule(to_s(_interval));
    }
};

ScheduledExecutor::ScheduledExecutor()
    : _threadPool(128_Ki),
      _transport(new FNET_Transport()),
      _lock(),
      _taskList()
{
    _transport->Start(&_threadPool);
}

ScheduledExecutor::~ScheduledExecutor()
{
    std::lock_guard guard(_lock);
    _transport->ShutDown(true);
    _threadPool.Close();
    _taskList.clear();
}


void
ScheduledExecutor::scheduleAtFixedRate(vespalib::Executor::Task::UP task, duration delay, duration interval)
{
    std::lock_guard guard(_lock);
    TimerTaskPtr tTask(new TimerTask(_transport->GetScheduler(), std::move(task), interval));
    _taskList.push_back(std::move(tTask));
    _taskList.back()->Schedule(to_s(delay));
}

void
ScheduledExecutor::reset()
{
    std::lock_guard guard(_lock);
    _transport->ShutDown(true);
    _taskList.clear();
    _transport = std::make_unique<FNET_Transport>();
    _transport->Start(&_threadPool);
}

}
