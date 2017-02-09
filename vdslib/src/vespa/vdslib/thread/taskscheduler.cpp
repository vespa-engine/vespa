// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "taskscheduler.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace vdslib {

uint64_t
TaskScheduler::Watch::getTime() const
{
    struct timeval mytime;
    gettimeofday(&mytime, 0);
    return mytime.tv_sec * 1000llu + mytime.tv_usec / 1000;
}

TaskScheduler::TaskScheduler()
    : _lock(),
      _defaultWatch(),
      _watch(&_defaultWatch),
      _tasks(),
      _currentRunningTasks(),
      _taskCounter(0)
{
}

bool TaskScheduler::onStop()
{
    vespalib::MonitorGuard guard(_lock);
    guard.broadcast();
    return true;
}

TaskScheduler::~TaskScheduler()
{
    stop();
    {
        vespalib::MonitorGuard guard(_lock);
        guard.broadcast();
    }
    join();
    for (TaskMap::iterator it = _tasks.begin(); it != _tasks.end(); ++it) {
        TaskVector & v(it->second);
        for (TaskVector::iterator it2 = v.begin(); it2 != v.end(); ++it2) {
            delete *it2;
        }
    }
}

void
TaskScheduler::add(Task::UP task)
{
    vespalib::MonitorGuard guard(_lock);
    std::vector<Task*>& tasks(_tasks[_watch->getTime()]);
    tasks.push_back(task.release());
    guard.broadcast();
}

void
TaskScheduler::addRelative(Task::UP task, Time timeDiff)
{
    vespalib::MonitorGuard guard(_lock);
    std::vector<Task*>& tasks(_tasks[_watch->getTime() + timeDiff]);
    tasks.push_back(task.release());
    guard.broadcast();
}

void
TaskScheduler::addAbsolute(Task::UP task, Time time)
{
    vespalib::MonitorGuard guard(_lock);
    std::vector<Task*>& tasks(_tasks[time]);
    tasks.push_back(task.release());
    guard.broadcast();
}

namespace {
    template<typename T>
    bool contains(const std::vector<T>& source, const T& element) {
        for (size_t i = 0, n = source.size(); i<n; ++i) {
            if (source[i] == element) return true;
        }
        return false;
    }

    template<typename T>
    void erase(std::vector<T>& source, const T& element) {
        std::vector<T> result;
        result.reserve(source.size());
        for (size_t i = 0, n = source.size(); i<n; ++i) {
            if (source[i] != element) result.push_back(source[i]);
        }
        result.swap(source);
    }
}

void
TaskScheduler::remove(Task* task)
{
    vespalib::MonitorGuard guard(_lock);
    while (contains(_currentRunningTasks, task)) {
        guard.wait();
    }
    for (TaskMap::iterator it = _tasks.begin(); it != _tasks.end();) {
        if (contains(it->second, task)) {
            erase(it->second, task);
            if (it->second.size() == 0) _tasks.erase(it);
            delete task;
            break;
        }
        ++it;
    }
}

void
TaskScheduler::setWatch(const Watch& watch)
{
    vespalib::MonitorGuard guard(_lock);
    _watch = &watch;
}

TaskScheduler::Time
TaskScheduler::getTime() const
{
    vespalib::MonitorGuard guard(_lock);
    return _watch->getTime();
}

uint64_t
TaskScheduler::getTaskCounter() const
{
    vespalib::MonitorGuard guard(_lock);
    return _taskCounter;
}

void
TaskScheduler::waitForTaskCounterOfAtLeast(uint64_t taskCounter,
                                           uint64_t timeout) const
{
    vespalib::MonitorGuard guard(_lock);
    uint64_t currentTime = _defaultWatch.getTime();
    uint64_t endTime = currentTime + timeout;
    while (_taskCounter < taskCounter) {
        if (endTime <= currentTime) {
            vespalib::asciistream ost;
            ost << "Task scheduler not reached task counter of " << taskCounter
                << " within timeout of " << timeout << " ms. Current task"
                << " counter is " << _taskCounter;
            throw vespalib::IllegalStateException(ost.str(), VESPA_STRLOC);
        }
        guard.wait(endTime - currentTime);
        currentTime = _defaultWatch.getTime();
    }
}

void
TaskScheduler::waitUntilNoTasksRemaining(uint64_t timeout) const
{
    vespalib::MonitorGuard guard(_lock);
    uint64_t currentTime = _defaultWatch.getTime();
    uint64_t endTime = currentTime + timeout;
    while (_tasks.size() > 0 || _currentRunningTasks.size() > 0) {
        if (endTime <= currentTime) {
            vespalib::asciistream ost;
            ost << "Task scheduler still have tasks scheduled after timeout"
                << " of " << timeout << " ms. There are " << _tasks.size()
                << " entries in tasks map and " << _currentRunningTasks.size()
                << " tasks currently scheduled to run.";
            throw vespalib::IllegalStateException(ost.str(), VESPA_STRLOC);
        }
        guard.wait(endTime - currentTime);
        currentTime = _defaultWatch.getTime();
    }
}

void
TaskScheduler::run()
{
    while (1) {
        vespalib::MonitorGuard guard(_lock);
        if (!running()) return;
        Time time = _watch->getTime();
        TaskMap::iterator next = _tasks.begin();
        if (next == _tasks.end()) {
            guard.wait();
            continue;
        }
        if (next->first > time) {
            guard.wait(next->first - time);
            continue;
        }
        TaskVector taskList(next->second);
        _currentRunningTasks.swap(next->second);
        _tasks.erase(next);
        guard.unlock();
        for (size_t i=0; i<taskList.size(); ++i) {
            int64_t result = taskList[i]->run(time);
            if (result < 0) {
                addAbsolute(Task::UP(taskList[i]),
                            time + (-1 * result));
            } else if (result > 0) {
                if (static_cast<Time>(result) <= time) {
                    taskList.push_back(taskList[i]);
                } else {
                    addAbsolute(Task::UP(taskList[i]), result);
                }
            } else {
                delete taskList[i];
            }
        }
        vespalib::MonitorGuard guard2(_lock);
        if (!running()) return;
        _taskCounter += _currentRunningTasks.size();
        _currentRunningTasks.clear();
        guard2.broadcast();
    }
}

} // vdslib
