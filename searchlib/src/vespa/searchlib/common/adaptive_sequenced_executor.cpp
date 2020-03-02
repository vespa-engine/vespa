// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "adaptive_sequenced_executor.h"

namespace search {

//-----------------------------------------------------------------------------

AdaptiveSequencedExecutor::Strand::Strand()
    : state(State::IDLE),
      queue()
{
}

AdaptiveSequencedExecutor::Strand::~Strand()
{
    assert(queue.empty());
}

//-----------------------------------------------------------------------------

AdaptiveSequencedExecutor::Worker::Worker()
    : cond(),
      state(State::RUNNING),
      strand(nullptr)
{
}

AdaptiveSequencedExecutor::Worker::~Worker()
{
    assert(state == State::DONE);
    assert(strand == nullptr);
}

//-----------------------------------------------------------------------------

AdaptiveSequencedExecutor::Self::Self()
    : cond(),
      state(State::OPEN),
      waiting_tasks(0),
      pending_tasks(0)
{
}

AdaptiveSequencedExecutor::Self::~Self()
{
    assert(state == State::CLOSED);
    assert(waiting_tasks == 0);
    assert(pending_tasks == 0);
}

//-----------------------------------------------------------------------------

AdaptiveSequencedExecutor::ThreadTools::ThreadTools(AdaptiveSequencedExecutor &parent_in)
    : parent(parent_in),
      pool(std::make_unique<FastOS_ThreadPool>(STACK_SIZE)),
      allow_worker_exit()
{
}

AdaptiveSequencedExecutor::ThreadTools::~ThreadTools()
{
    assert(pool->isClosed());
}

void
AdaptiveSequencedExecutor::ThreadTools::Run(FastOS_ThreadInterface *, void *)
{
    parent.worker_main();
}

void
AdaptiveSequencedExecutor::ThreadTools::start(size_t num_threads)
{
    for (size_t i = 0; i < num_threads; ++i) {
        FastOS_ThreadInterface *thread = pool->NewThread(this);
        assert(thread != nullptr);
        (void)thread;
    }
}

void
AdaptiveSequencedExecutor::ThreadTools::close()
{
    allow_worker_exit.countDown();
    pool->Close();
}

//-----------------------------------------------------------------------------

void
AdaptiveSequencedExecutor::maybe_block_self(std::unique_lock<std::mutex> &lock)
{
    while (_self.state == Self::State::BLOCKED) {
        _self.cond.wait(lock);
    }
    while ((_self.state == Self::State::OPEN) && (_self.pending_tasks >= _cfg.max_pending)) {
        _self.state = Self::State::BLOCKED;
        while (_self.state == Self::State::BLOCKED) {
            _self.cond.wait(lock);
        }
    }
}

bool
AdaptiveSequencedExecutor::maybe_unblock_self(const std::unique_lock<std::mutex> &)
{
    if ((_self.state == Self::State::BLOCKED) && (_self.pending_tasks < _cfg.wakeup_limit)) {
        _self.state = Self::State::OPEN;
        return true;
    }
    return false;
}

AdaptiveSequencedExecutor::Worker *
AdaptiveSequencedExecutor::get_worker_to_wake(const std::unique_lock<std::mutex> &)
{
    if ((_self.waiting_tasks > _cfg.max_waiting) && (!_worker_stack.empty())) {
        assert(!_wait_queue.empty());
        Worker *worker = _worker_stack.back();
        _worker_stack.popBack();
        assert(worker->state == Worker::State::BLOCKED);
        assert(worker->strand == nullptr);
        worker->state = Worker::State::RUNNING;
        worker->strand = _wait_queue.front();
        _wait_queue.pop();
        assert(worker->strand->state == Strand::State::WAITING);
        assert(!worker->strand->queue.empty());
        worker->strand->state = Strand::State::ACTIVE;
        assert(_self.waiting_tasks >= worker->strand->queue.size());
        _self.waiting_tasks -= worker->strand->queue.size();
        return worker;
    }
    return nullptr;
}

bool
AdaptiveSequencedExecutor::obtain_strand(Worker &worker, std::unique_lock<std::mutex> &lock)
{
    assert(worker.strand == nullptr);
    if (!_wait_queue.empty()) {
        worker.strand = _wait_queue.front();
        _wait_queue.pop();
        assert(worker.strand->state == Strand::State::WAITING);
        assert(!worker.strand->queue.empty());
        worker.strand->state = Strand::State::ACTIVE;
        assert(_self.waiting_tasks >= worker.strand->queue.size());
        _self.waiting_tasks -= worker.strand->queue.size();
    } else if (_self.state == Self::State::CLOSED) {
        worker.state = Worker::State::DONE;
    } else {
        worker.state = Worker::State::BLOCKED;
        _worker_stack.push(&worker);
        while (worker.state == Worker::State::BLOCKED) {
            worker.cond.wait(lock);
        }
    }
    return (worker.state == Worker::State::RUNNING);
}

bool
AdaptiveSequencedExecutor::exchange_strand(Worker &worker, std::unique_lock<std::mutex> &lock)
{
    if (worker.strand == nullptr) {
        return obtain_strand(worker, lock);
    }
    if (worker.strand->queue.empty()) {
        worker.strand->state = Strand::State::IDLE;
        worker.strand = nullptr;
        return obtain_strand(worker, lock);
    }
    if (!_wait_queue.empty()) {
        worker.strand->state = Strand::State::WAITING;
        _self.waiting_tasks += worker.strand->queue.size();
        _wait_queue.push(worker.strand);
        worker.strand = nullptr;
        return obtain_strand(worker, lock);
    }
    return true;
}

AdaptiveSequencedExecutor::Task::UP
AdaptiveSequencedExecutor::next_task(Worker &worker)
{
    Task::UP task;
    Worker *worker_to_wake = nullptr;
    auto guard = std::unique_lock(_mutex);
    if (exchange_strand(worker, guard)) {
        assert(worker.state == Worker::State::RUNNING);
        assert(worker.strand != nullptr);
        assert(!worker.strand->queue.empty());
        task = std::move(worker.strand->queue.front());
        worker.strand->queue.pop();
        _stats.queueSize.add(--_self.pending_tasks);
        worker_to_wake = get_worker_to_wake(guard);
    } else {
        assert(worker.state == Worker::State::DONE);
        assert(worker.strand == nullptr);
    }
    bool signal_self = maybe_unblock_self(guard);
    guard.unlock(); // UNLOCK
    if (worker_to_wake != nullptr) {
        worker_to_wake->cond.notify_one();
    }
    if (signal_self) {
        _self.cond.notify_all();
    }
    return task;
}

void
AdaptiveSequencedExecutor::worker_main()
{
    Worker worker;
    while (Task::UP my_task = next_task(worker)) {
        my_task->run();
    }
    _thread_tools->allow_worker_exit.await();
}

AdaptiveSequencedExecutor::AdaptiveSequencedExecutor(size_t num_strands, size_t num_threads,
                                                     size_t max_waiting, size_t max_pending)
    : ISequencedTaskExecutor(num_strands),
      _thread_tools(std::make_unique<ThreadTools>(*this)),
      _mutex(),
      _strands(num_strands),
      _wait_queue(num_strands),
      _worker_stack(num_threads),
      _self(),
      _stats(),
      _cfg(num_threads, max_waiting, max_pending)
{
    _stats.queueSize.add(_self.pending_tasks);
    _thread_tools->start(num_threads);
}

AdaptiveSequencedExecutor::~AdaptiveSequencedExecutor()
{
    sync();
    {
        auto guard = std::unique_lock(_mutex);
        assert(_self.state == Self::State::OPEN);
        _self.state = Self::State::CLOSED;
        while (!_worker_stack.empty()) {
            Worker *worker = _worker_stack.back();
            _worker_stack.popBack();
            assert(worker->state == Worker::State::BLOCKED);
            assert(worker->strand == nullptr);
            worker->state = Worker::State::DONE;
            worker->cond.notify_one();
        }
        _self.cond.notify_all();
    }
    _thread_tools->close();
    assert(_wait_queue.empty());
    assert(_worker_stack.empty());
}

void
AdaptiveSequencedExecutor::executeTask(ExecutorId id, Task::UP task)
{
    assert(id.getId() < _strands.size());
    Strand &strand = _strands[id.getId()];
    auto guard = std::unique_lock(_mutex);
    maybe_block_self(guard);
    assert(_self.state != Self::State::CLOSED);
    strand.queue.push(std::move(task));
    _stats.queueSize.add(++_self.pending_tasks);
    ++_stats.acceptedTasks;
    if (strand.state == Strand::State::WAITING) {
        ++_self.waiting_tasks;
    } else if (strand.state == Strand::State::IDLE) {
        if (_worker_stack.size() < _cfg.num_threads) {
            strand.state = Strand::State::WAITING;
            _wait_queue.push(&strand);
            _self.waiting_tasks += strand.queue.size();
        } else {
            strand.state = Strand::State::ACTIVE;
            assert(_wait_queue.empty());
            Worker *worker = _worker_stack.back();
            _worker_stack.popBack();
            assert(worker->state == Worker::State::BLOCKED);
            assert(worker->strand == nullptr);
            worker->state = Worker::State::RUNNING;
            worker->strand = &strand;
            guard.unlock(); // UNLOCK
            worker->cond.notify_one();
        }
    }
}

void
AdaptiveSequencedExecutor::sync()
{
    vespalib::CountDownLatch latch(_strands.size());
    for (size_t i = 0; i < _strands.size(); ++i) {
        execute(ExecutorId(i), [&](){ latch.countDown(); });
    }
    latch.await();
}

void
AdaptiveSequencedExecutor::setTaskLimit(uint32_t task_limit)
{
    auto guard = std::unique_lock(_mutex);   
    _cfg.set_max_pending(task_limit);
    bool signal_self = maybe_unblock_self(guard);
    guard.unlock(); // UNLOCK
    if (signal_self) {
        _self.cond.notify_all();
    }
}

AdaptiveSequencedExecutor::Stats
AdaptiveSequencedExecutor::getStats()
{
    auto guard = std::lock_guard(_mutex);
    Stats stats = _stats;
    _stats = Stats();
    _stats.queueSize.add(_self.pending_tasks);
    return stats;
}

}
