// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

ThreadStackExecutorBase::Worker::Worker()
    : lock(),
      cond(),
      idleTracker(),
      pre_guard(0xaaaaaaaa),
      idle(true),
      post_guard(0x55555555),
      task()
{}

void
ThreadStackExecutorBase::Worker::verify(bool expect_idle) const {
    (void) expect_idle;
    assert(pre_guard == 0xaaaaaaaa);
    assert(post_guard == 0x55555555);
    assert(idle == expect_idle);
    assert(!task.task == expect_idle);
}

void
ThreadStackExecutorBase::BlockedThread::wait() const
{
    unique_lock guard(lock);
    while (blocked) {
        cond.wait(guard);
    }
}

void
ThreadStackExecutorBase::BlockedThread::unblock()
{
    unique_lock guard(lock);
    blocked = false;
    cond.notify_one();
}

//-----------------------------------------------------------------------------

thread_local ThreadStackExecutorBase *ThreadStackExecutorBase::_master = nullptr;

//-----------------------------------------------------------------------------

void
ThreadStackExecutorBase::block_thread(const unique_lock &, BlockedThread &blocked_thread)
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
ThreadStackExecutorBase::unblock_threads(const unique_lock &)
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
    unique_lock guard(worker.lock);
    worker.verify(/* idle: */ true);
    worker.idle = false;
    worker.task = std::move(task);
    worker.cond.notify_one();
}

bool
ThreadStackExecutorBase::obtainTask(Worker &worker)
{
    {
        unique_lock guard(_lock);
        if (!worker.idle) {
            assert(_taskCount != 0);
            --_taskCount;
            wakeup(guard, _cond);
            _barrier.completeEvent(worker.task.token);
            worker.idle = true;
        }
        worker.verify(/* idle: */ true);
        unblock_threads(guard);
        if (!_tasks.empty()) {
            worker.task = std::move(_tasks.front());
            worker.idle = false;
            _tasks.pop();
            return true;
        }
        if (_closed) {
            return false;
        }
        _workers.push(&worker);
        worker.idleTracker.set_idle(steady_clock::now());
    }
    {
        unique_lock guard(worker.lock);
        while (worker.idle) {
            worker.cond.wait(guard);
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
      _lock(),
      _cond(),
      _stats(),
      _idleTracker(steady_clock::now()),
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

size_t
ThreadStackExecutorBase::getNumThreads() const {
    return _pool->GetNumStartedThreads();
}

void
ThreadStackExecutorBase::setTaskLimit(uint32_t taskLimit)
{
    internalSetTaskLimit(taskLimit);
}

uint32_t
ThreadStackExecutorBase::getTaskLimit() const
{
    unique_lock guard(_lock);
    return _taskLimit;
}

void
ThreadStackExecutorBase::wakeup() {
    // Nothing to do here as workers are always attentive.
}

void
ThreadStackExecutorBase::internalSetTaskLimit(uint32_t taskLimit)
{
    unique_lock guard(_lock);
    if (!_closed) {
        _taskLimit = taskLimit;
        wakeup(guard, _cond);
    }
}

size_t
ThreadStackExecutorBase::num_idle_workers() const
{
    std::unique_lock guard(_lock);
    return _workers.size();
}

ExecutorStats
ThreadStackExecutorBase::getStats()
{
    std::unique_lock guard(_lock);
    ExecutorStats stats = _stats;
    steady_time now = steady_clock::now();
    for (size_t i(0); i < _workers.size(); i++) {
        _idleTracker.was_idle(_workers.access(i)->idleTracker.reset(now));
    }
    size_t numThreads = getNumThreads();
    stats.setUtil(numThreads, _idleTracker.reset(now, numThreads));
    _stats = ExecutorStats();
    _stats.queueSize.add(_taskCount);
    return stats;
}

ThreadStackExecutorBase::Task::UP
ThreadStackExecutorBase::execute(Task::UP task)
{
    unique_lock guard(_lock);
    if (acceptNewTask(guard, _cond)) {
        TaggedTask taggedTask(std::move(task), _barrier.startEvent());
        ++_taskCount;
        ++_stats.acceptedTasks;
        _stats.queueSize.add(_taskCount);
        if (!_workers.empty()) {
            Worker *worker = _workers.back();
            _workers.popBack();
            _idleTracker.was_idle(worker->idleTracker.set_active(steady_clock::now()));
            _stats.wakeupCount++;
            guard.unlock(); // <- UNLOCK
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
        unique_lock guard(_lock);
        _closed = true;
        _taskLimit = 0;
        idle.swap(_workers);
        assert(idle.empty() || _tasks.empty()); // idle -> empty queue
        wakeup(guard, _cond);
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
        std::unique_lock guard(_lock);
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
    std::unique_lock guard(_lock);
    if (_taskCount <= task_count) {
        return;
    }
    BlockedThread self(task_count);
    block_thread(guard, self);
    guard.unlock(); // <- UNLOCK
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
