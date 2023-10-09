// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "scheduledexecutor.h"
#include <vespa/fnet/scheduler.h>
#include <vespa/fnet/task.h>
#include <vespa/fnet/transport.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

using vespalib::duration;

namespace proton {

using Task = vespalib::Executor::Task;

class TimerTask : public FNET_Task
{
private:
    Task::UP        _task;
    duration        _interval;
public:
    TimerTask(const TimerTask &) = delete;
    TimerTask&operator=(const TimerTask &) = delete;
    TimerTask(FNET_Scheduler *scheduler, Task::UP task, duration interval)
        : FNET_Task(scheduler),
          _task(std::move(task)),
          _interval(interval)
    { }

    ~TimerTask() override {
        Kill();
    }

    void PerformTask() override {
        _task->run();
        Schedule(vespalib::to_s(_interval));
    }
};

class ScheduledExecutor::Registration : public vespalib::IDestructorCallback {
private:
    ScheduledExecutor & _executor;
    uint64_t            _key;
public:
    Registration(ScheduledExecutor & executor, uint64_t key) : _executor(executor), _key(key) {}
    ~Registration() {
        _executor.cancel(_key);
    }
};

ScheduledExecutor::ScheduledExecutor(FNET_Transport & transport)
    : _transport(transport),
      _lock(),
      _nextKey(0),
      _taskList()
{ }

ScheduledExecutor::~ScheduledExecutor()
{
    std::lock_guard guard(_lock);
    assert(_taskList.empty());
}


IScheduledExecutor::Handle
ScheduledExecutor::scheduleAtFixedRate(Executor::Task::UP task, duration delay, duration interval)
{
    std::lock_guard guard(_lock);
    uint64_t key = _nextKey++;
    auto tTask = std::make_unique<TimerTask>(_transport.GetScheduler(), std::move(task), interval);
    auto & taskRef = *tTask;
    _taskList[key] = std::move(tTask);
    taskRef.Schedule(vespalib::to_s(delay));
    return std::make_unique<Registration>(*this, key);
}

bool
ScheduledExecutor::cancel(uint64_t key)
{
    std::lock_guard guard(_lock);
    auto found = _taskList.find(key);
    if (found == _taskList.end()) return false;

    found->second->Unschedule();
    _taskList.erase(found);
    return true;
}

}
