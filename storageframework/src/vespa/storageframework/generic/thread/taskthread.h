// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Implementation of ticking threads for performing prioritized tasks.
 * Implements critical section and a prioritized queue for communication
 * outside of thread.
 *
 * Note that doNonCriticalTick is not implemented to call a processTask()
 * function, as applications might want to do something even if there is no
 * task, prioritize something above a task at some time, or process multiple
 * tasks in one tick (to reduce locking overhead).. Thus we expect most apps to
 * want to implement doNonCriticalTick() anyhow, so rather we just make
 * available functions for peeking and extracting tasks.
 */

#pragma once

#include <queue>
#include <vespa/storageframework/generic/thread/tickingthread.h>

namespace storage {
namespace framework {

template <typename Task>
class TaskThread : public TickingThread {
    ThreadLock& _lock;
    std::vector<Task> _enqueued;
    std::priority_queue<Task> _tasks;

public:
    TaskThread(ThreadLock& lock);
    
    void addTask(const Task& t);
    ThreadWaitInfo doCriticalTick(ThreadIndex) override;
    
    bool empty() const { return _tasks.empty(); }
    const Task& peek() const { return _tasks.top(); }
    void pop() { _tasks.pop(); }

private:
    virtual ThreadWaitInfo doNonCriticalTick(ThreadIndex) override = 0;
};

template <typename Task>
TaskThread<Task>::TaskThread(ThreadLock& lock)
    : _lock(lock)
{
}
    
template <typename Task>
void
TaskThread<Task>::addTask(const Task& t)
{
    TickingLockGuard lock(_lock.freezeCriticalTicks());
    _enqueued.push_back(t);
    lock.broadcast();
}

template <typename Task>
ThreadWaitInfo
TaskThread<Task>::doCriticalTick(ThreadIndex) {
    std::vector<Task> enqueued;
    enqueued.swap(_enqueued);
    for (size_t i=0, n=enqueued.size(); i<n; ++i) {
        _tasks.push(enqueued[i]);
    }
    return ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN;
}


} // framework
} // storage
