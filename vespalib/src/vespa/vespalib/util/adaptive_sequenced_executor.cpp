// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "adaptive_sequenced_executor.h"

namespace vespalib {

//-----------------------------------------------------------------------------

AdaptiveSequencedExecutor::Strand::Strand() noexcept
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
      idleTracker(),
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
      pool(),
      allow_worker_exit()
{
}

AdaptiveSequencedExecutor::ThreadTools::~ThreadTools()
{
}

void
AdaptiveSequencedExecutor::ThreadTools::start(size_t num_threads)
{
    for (size_t i = 0; i < num_threads; ++i) {
        pool.start([this](){ parent.worker_main(); });
    }
}

void
AdaptiveSequencedExecutor::ThreadTools::close()
{
    allow_worker_exit.countDown();
    pool.join();
}

//-----------------------------------------------------------------------------

void
AdaptiveSequencedExecutor::maybe_block_self(std::unique_lock<std::mutex> &lock)
{
    while (_self.state == Self::State::BLOCKED) {
        _self.cond.wait(lock);
    }
    while ((_self.state == Self::State::OPEN) && _cfg.is_above_max_pending(_self.pending_tasks)) {
        _self.state = Self::State::BLOCKED;
        while (_self.state == Self::State::BLOCKED) {
            _self.cond.wait(lock);
        }
    }
}

void
AdaptiveSequencedExecutor::maybe_unblock_self(const std::unique_lock<std::mutex> &)
{
    if ((_self.state == Self::State::BLOCKED) && (_self.pending_tasks < _cfg.wakeup_limit)) {
        _self.state = Self::State::OPEN;
        _self.cond.notify_all();
    }
}

void
AdaptiveSequencedExecutor::maybe_wake_worker(const std::unique_lock<std::mutex> &)
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
        worker->cond.notify_one();
    }
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
        worker.idleTracker.set_idle(steady_clock::now());
        while (worker.state == Worker::State::BLOCKED) {
            worker.cond.wait(lock);
        }
        _idleTracker.was_idle(worker.idleTracker.set_active(steady_clock::now()));
        _stats.wakeupCount++;
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

AdaptiveSequencedExecutor::TaggedTask
AdaptiveSequencedExecutor::next_task(Worker &worker, std::optional<uint32_t> prev_token)
{
    TaggedTask task;
    auto guard = std::unique_lock(_mutex);
    if (prev_token.has_value()) {
        _barrier.completeEvent(prev_token.value());
    }
    if (exchange_strand(worker, guard)) {
        assert(worker.state == Worker::State::RUNNING);
        assert(worker.strand != nullptr);
        assert(!worker.strand->queue.empty());
        task = std::move(worker.strand->queue.front());
        worker.strand->queue.pop();
        _stats.queueSize.add(--_self.pending_tasks);
        maybe_wake_worker(guard);
    } else {
        assert(worker.state == Worker::State::DONE);
        assert(worker.strand == nullptr);
    }
    maybe_unblock_self(guard);
    return task;
}

void
AdaptiveSequencedExecutor::worker_main()
{
    Worker worker;
    std::optional<uint32_t> prev_token = std::nullopt;
    while (TaggedTask my_task = next_task(worker, prev_token)) {
        my_task.task->run();
        prev_token = my_task.token;
    }
    _thread_tools->allow_worker_exit.await();
}

AdaptiveSequencedExecutor::AdaptiveSequencedExecutor(size_t num_strands, size_t num_threads,
                                                     size_t max_waiting, size_t max_pending,
                                                     bool is_max_pending_hard)
    : ISequencedTaskExecutor(num_strands),
      _thread_tools(std::make_unique<ThreadTools>(*this)),
      _mutex(),
      _strands(num_strands),
      _wait_queue(num_strands),
      _worker_stack(num_threads),
      _self(),
      _stats(),
      _idleTracker(steady_clock::now()),
      _cfg(num_threads, max_waiting, max_pending, is_max_pending_hard)
{
    _stats.queueSize.add(_self.pending_tasks);
    _thread_tools->start(num_threads);
}

AdaptiveSequencedExecutor::~AdaptiveSequencedExecutor()
{
    sync_all();
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

ISequencedTaskExecutor::ExecutorId
AdaptiveSequencedExecutor::getExecutorId(uint64_t component) const {
    return ExecutorId(component % _strands.size());
}

void
AdaptiveSequencedExecutor::executeTask(ExecutorId id, Task::UP task)
{
    assert(id.getId() < _strands.size());
    Strand &strand = _strands[id.getId()];
    auto guard = std::unique_lock(_mutex);
    assert(_self.state != Self::State::CLOSED);
    maybe_block_self(guard);
    strand.queue.push(TaggedTask(std::move(task), _barrier.startEvent()));
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
            worker->cond.notify_one();
        }
    }
}

void
AdaptiveSequencedExecutor::sync_all()
{
    BarrierCompletion barrierCompletion;
    {
        auto guard = std::lock_guard(_mutex);
        if (!_barrier.startBarrier(barrierCompletion)) {
            return;
        }
    }
    barrierCompletion.gate.await();
}

void
AdaptiveSequencedExecutor::setTaskLimit(uint32_t task_limit)
{
    auto guard = std::unique_lock(_mutex);   
    _cfg.set_max_pending(task_limit);
    maybe_unblock_self(guard);
}

ExecutorStats
AdaptiveSequencedExecutor::getStats()
{
    auto guard = std::lock_guard(_mutex);
    ExecutorStats stats = _stats;
    steady_time now = steady_clock::now();
    for (size_t i(0); i < _worker_stack.size(); i++) {
        _idleTracker.was_idle(_worker_stack.access(i)->idleTracker.reset(now));
    }
    stats.setUtil(_cfg.num_threads, _idleTracker.reset(now, _cfg.num_threads));
    _stats = ExecutorStats();
    _stats.queueSize.add(_self.pending_tasks);
    return stats;
}

AdaptiveSequencedExecutor::Config
AdaptiveSequencedExecutor::get_config() const
{
    auto guard = std::lock_guard(_mutex);
    return _cfg;
}

}
