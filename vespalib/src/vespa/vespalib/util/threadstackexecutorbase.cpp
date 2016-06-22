// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "threadstackexecutorbase.h"

namespace vespalib {

void
ThreadStackExecutorBase::BlockedThread::wait() const
{
    MonitorGuard guard(monitor);
    while (blocked) {
        guard.wait();
    }
}

void
ThreadStackExecutorBase::BlockedThread::unblock()
{
    MonitorGuard guard(monitor);
    blocked = false;
    guard.signal();
}

//-----------------------------------------------------------------------------

void
ThreadStackExecutorBase::block_thread(const LockGuard &, BlockedThread &blocked_thread)
{
    auto pos = _blocked.begin();
    while ((pos != _blocked.end()) &&
           ((*pos)->wait_task_count < blocked_thread.wait_task_count))
    {
        ++pos;
    }
    _blocked.insert(pos, &blocked_thread);
}

void
ThreadStackExecutorBase::unblock_threads(const MonitorGuard &)
{
    while (!_blocked.empty() && (_taskCount <= _blocked.back()->wait_task_count)) {
        BlockedThread &blocked_thread = *(_blocked.back());
        _blocked.pop_back();
        blocked_thread.unblock();
    }
}

void
ThreadStackExecutorBase::assignTask(const TaggedTask &task, Worker &worker)
{
    MonitorGuard monitor(worker.monitor);
    assert(worker.idle);
    assert(worker.task.task == 0);
    worker.idle = false;
    worker.task = task;
    monitor.signal();
}

bool
ThreadStackExecutorBase::obtainTask(Worker &worker)
{
    {
        MonitorGuard monitor(_monitor);
        if (worker.task.task != 0) {
            --_taskCount;
            _barrier.completeEvent(worker.task.token);
            worker.task.task = 0;
        }
        unblock_threads(monitor);
        if (!_tasks.empty()) {
            worker.task = _tasks.front();
            _tasks.pop();
            wakeup(monitor);
            return true;
        }
        if (_closed) {
            return false;
        }
        worker.idle = true;
        _workers.push(&worker);
    }
    {
        MonitorGuard monitor(worker.monitor);
        while (worker.idle) {
            monitor.wait();
        }
    }
    return (worker.task.task != 0);
}

void
ThreadStackExecutorBase::Run(FastOS_ThreadInterface *, void *)
{
    Worker worker;
    while (obtainTask(worker)) {
        worker.task.task->run();
        delete worker.task.task;
    }
    _executorCompletion.await(); // to allow unsafe signaling
}

//-----------------------------------------------------------------------------

ThreadStackExecutorBase::ThreadStackExecutorBase(uint32_t stackSize,
                                                 uint32_t taskLimit)
    : _pool(stackSize),
      _monitor(),
      _stats(),
      _executorCompletion(),
      _tasks(),
      _workers(),
      _barrier(),
      _taskCount(0),
      _taskLimit(taskLimit),
      _closed(false)
{
    assert(taskLimit > 0);
}

void
ThreadStackExecutorBase::start(uint32_t threads)
{
    assert(threads > 0);
    for (uint32_t i = 0; i < threads; ++i) {
        FastOS_ThreadInterface *thread = _pool.NewThread(this);
        assert(thread != 0);
        (void)thread;
    }
}

ThreadStackExecutorBase::Stats
ThreadStackExecutorBase::getStats()
{
    LockGuard lock(_monitor);
    Stats stats = _stats;
    _stats = Stats();
    _stats.maxPendingTasks = _taskCount;
    return stats;
}

bool
ThreadStackExecutorBase::acceptNewTask(MonitorGuard & guard)
{
    (void) guard;
    return (_taskCount < _taskLimit);
}

void
ThreadStackExecutorBase::wakeup(MonitorGuard & monitor)
{
    (void) monitor;
}

ThreadStackExecutorBase::Task::UP
ThreadStackExecutorBase::execute(Task::UP task)
{
    MonitorGuard monitor(_monitor);
    if (acceptNewTask(monitor)) {
        TaggedTask taggedTask(task.release(), _barrier.startEvent());
        ++_taskCount;
        ++_stats.acceptedTasks;
        _stats.maxPendingTasks = (_taskCount > _stats.maxPendingTasks)
                                 ?_taskCount : _stats.maxPendingTasks;
        if (!_workers.empty()) {
            Worker *worker = _workers.back();
            _workers.popBack();
            monitor.unlock(); // <- UNLOCK
            assignTask(taggedTask, *worker);
        } else {
            _tasks.push(taggedTask);
        }
    } else {
        ++_stats.rejectedTasks;
    }
    return std::move(task);
}

ThreadStackExecutorBase &
ThreadStackExecutorBase::shutdown()
{
    ArrayQueue<Worker*> idle;
    {
        MonitorGuard monitor(_monitor);
        _closed = true;
        _taskLimit = 0;
        idle.swap(_workers);
        assert(idle.empty() || _tasks.empty()); // idle -> empty queue
        wakeup(monitor);
    }
    while (!idle.empty()) {
        assignTask(TaggedTask(), *idle.back());
        idle.popBack();
    }
    return *this;
}

ThreadStackExecutorBase &
ThreadStackExecutorBase::sync()
{
    BarrierCompletion barrierCompletion;
    {
        LockGuard lock(_monitor);
        if (!_barrier.startBarrier(barrierCompletion)) {
            return *this;
        }
    }
    barrierCompletion.gate.await();
    return *this;
}

void
ThreadStackExecutorBase::wait_for_task_count(uint32_t task_count)
{
    LockGuard lock(_monitor);
    if (_taskCount <= task_count) {
        return;
    }
    BlockedThread self(task_count);
    block_thread(lock, self);
    lock.unlock(); // <- UNLOCK
    self.wait();
}

void
ThreadStackExecutorBase::cleanup()
{
    shutdown().sync();
    _executorCompletion.countDown();
    _pool.Close();
}

ThreadStackExecutorBase::~ThreadStackExecutorBase()
{
    assert(_pool.isClosed());
    assert(_taskCount == 0);
    assert(_blocked.empty());
}

} // namespace vespalib
