// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threadstackexecutorbase.h"
#include <vespa/fastos/thread.h>

namespace vespalib {

namespace thread {

struct ThreadInit : public FastOS_Runnable {
    Runnable &worker;
    ThreadStackExecutorBase::init_fun_t init_fun;

    explicit ThreadInit(Runnable &worker_in, ThreadStackExecutorBase::init_fun_t init_fun_in)
            : worker(worker_in), init_fun(std::move(init_fun_in)) {}

    void Run(FastOS_ThreadInterface *, void *) override;
};

void
ThreadInit::Run(FastOS_ThreadInterface *, void *) {
    init_fun(worker);
}

}

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

thread_local ThreadStackExecutorBase *ThreadStackExecutorBase::_master = nullptr;

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
ThreadStackExecutorBase::assignTask(TaggedTask task, Worker &worker)
{
    MonitorGuard monitor(worker.monitor);
    worker.verify(/* idle: */ true);
    worker.idle = false;
    worker.task = std::move(task);
    monitor.signal();
}

bool
ThreadStackExecutorBase::obtainTask(Worker &worker)
{
    {
        MonitorGuard monitor(_monitor);
        if (!worker.idle) {
            assert(_taskCount != 0);
            --_taskCount;
            _barrier.completeEvent(worker.task.token);
            worker.idle = true;
        }
        worker.verify(/* idle: */ true);
        unblock_threads(monitor);
        if (!_tasks.empty()) {
            worker.task = std::move(_tasks.front());
            worker.idle = false;
            _tasks.pop();
            wakeup(monitor);
            return true;
        }
        if (_closed) {
            return false;
        }
        _workers.push(&worker);
    }
    {
        MonitorGuard monitor(worker.monitor);
        while (worker.idle) {
            monitor.wait();
        }
    }
    worker.idle = !worker.task.task;
    return !worker.idle;
}

void
ThreadStackExecutorBase::run()
{
    Worker worker;
    _master = this;
    worker.verify(/* idle: */ true);
    while (obtainTask(worker)) {
        worker.verify(/* idle: */ false);
        worker.task.task->run();
        worker.task.task.reset();
    }
    _executorCompletion.await(); // to allow unsafe signaling
    worker.verify(/* idle: */ true);
    _master = nullptr;
}

//-----------------------------------------------------------------------------

ThreadStackExecutorBase::ThreadStackExecutorBase(uint32_t stackSize,
                                                 uint32_t taskLimit,
                                                 init_fun_t init_fun)
    : SyncableThreadExecutor(),
      Runnable(),
      _pool(std::make_unique<FastOS_ThreadPool>(stackSize)),
      _monitor(),
      _stats(),
      _executorCompletion(),
      _tasks(),
      _workers(),
      _barrier(),
      _taskCount(0),
      _taskLimit(taskLimit),
      _closed(false),
      _thread_init(std::make_unique<thread::ThreadInit>(*this, std::move(init_fun)))
{
    assert(taskLimit > 0);
}

void
ThreadStackExecutorBase::start(uint32_t threads)
{
    assert(threads > 0);
    for (uint32_t i = 0; i < threads; ++i) {
        FastOS_ThreadInterface *thread = _pool->NewThread(_thread_init.get());
        assert(thread != nullptr);
        (void)thread;
    }
}

size_t ThreadStackExecutorBase::getNumThreads() const {
    return _pool->GetNumStartedThreads();
}

void
ThreadStackExecutorBase::setTaskLimit(uint32_t taskLimit)
{
    internalSetTaskLimit(taskLimit);
}

void
ThreadStackExecutorBase::internalSetTaskLimit(uint32_t taskLimit)
{
    MonitorGuard monitor(_monitor);
    if (!_closed) {
        _taskLimit = taskLimit;
        wakeup(monitor);
    }
}

size_t
ThreadStackExecutorBase::num_idle_workers() const
{
    LockGuard lock(_monitor);
    return _workers.size();
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

ThreadStackExecutorBase::Task::UP
ThreadStackExecutorBase::execute(Task::UP task)
{
    MonitorGuard monitor(_monitor);
    if (acceptNewTask(monitor)) {
        TaggedTask taggedTask(std::move(task), _barrier.startEvent());
        ++_taskCount;
        ++_stats.acceptedTasks;
        _stats.maxPendingTasks = (_taskCount > _stats.maxPendingTasks)
                                 ?_taskCount : _stats.maxPendingTasks;
        if (!_workers.empty()) {
            Worker *worker = _workers.back();
            _workers.popBack();
            monitor.unlock(); // <- UNLOCK
            assignTask(std::move(taggedTask), *worker);
        } else {
            _tasks.push(std::move(taggedTask));
        }
    } else {
        ++_stats.rejectedTasks;
    }
    return task;
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
    _pool->Close();
}

ThreadStackExecutorBase::~ThreadStackExecutorBase()
{
    assert(_pool->isClosed());
    assert(_taskCount == 0);
    assert(_blocked.empty());
}

} // namespace vespalib
