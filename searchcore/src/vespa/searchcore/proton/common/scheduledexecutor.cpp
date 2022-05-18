// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "scheduledexecutor.h"
#include <vespa/fnet/scheduler.h>
#include <vespa/fnet/task.h>
#include <vespa/fnet/transport.h>

using vespalib::duration;

namespace proton {

using Task = vespalib::Executor::Task;

class TimerTask : public FNET_Task
{
private:
    TimerTask(const TimerTask &);
    TimerTask&operator=(const TimerTask &);

    FNET_Scheduler *_scheduler;
    Task::UP        _task;
    duration        _interval;
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
        Schedule(vespalib::to_s(_interval));
    }
};

ScheduledExecutor::ScheduledExecutor(FNET_Transport & transport)
    : _transport(transport),
      _lock(),
      _taskList()
{ }

ScheduledExecutor::~ScheduledExecutor()
{
    reset();
}


void
ScheduledExecutor::scheduleAtFixedRate(vespalib::Executor::Task::UP task, duration delay, duration interval)
{
    std::lock_guard guard(_lock);
    auto tTask = std::make_unique<TimerTask>(_transport.GetScheduler(), std::move(task), interval);
    _taskList.push_back(std::move(tTask));
    _taskList.back()->Schedule(vespalib::to_s(delay));
}

void
ScheduledExecutor::reset()
{
    std::lock_guard guard(_lock);
    for (auto & task : _taskList) {
        task->Unschedule();
    }
    _taskList.clear();
}

}
