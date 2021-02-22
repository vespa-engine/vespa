// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "singleexecutor.h"
#include <vespa/vespalib/util/alloc.h>
#include <cassert>

namespace vespalib {

SingleExecutor::SingleExecutor(init_fun_t func, uint32_t taskLimit)
    : SingleExecutor(func, taskLimit, taskLimit/10, 5ms)
{ } 

SingleExecutor::SingleExecutor(init_fun_t func, uint32_t taskLimit, uint32_t watermark, duration reactionTime)
    : _taskLimit(vespalib::roundUp2inN(taskLimit)),
      _wantedTaskLimit(_taskLimit.load()),
      _rp(0),
      _tasks(std::make_unique<Task::UP[]>(_taskLimit)),
      _mutex(),
      _consumerCondition(),
      _producerCondition(),
      _thread(*this),
      _lastAccepted(0),
      _queueSize(),
      _wakeupConsumerAt(0),
      _producerNeedWakeupAt(0),
      _wp(0),
      _watermark(std::min(_taskLimit.load(), watermark)),
      _reactionTime(reactionTime),
      _closed(false)
{
    (void) func; //TODO implement similar to ThreadStackExecutor
    assert(taskLimit >= watermark);
    _thread.start();
}

SingleExecutor::~SingleExecutor() {
    shutdown();
    sync();
    _thread.stop().join();
}

size_t
SingleExecutor::getNumThreads() const {
    return 1;
}

void
SingleExecutor::sleepProducer(Lock & lock, duration maxWaitTime, uint64_t wakeupAt) {
    _producerNeedWakeupAt.store(wakeupAt, std::memory_order_relaxed);
    _producerCondition.wait_for(lock, maxWaitTime);
    _producerNeedWakeupAt.store(0, std::memory_order_relaxed);
}

Executor::Task::UP
SingleExecutor::execute(Task::UP task) {
    uint64_t wp;
    {
        Lock guard(_mutex);
        if (_closed) {
            return task;
        }
        wait_for_room(guard);
        wp = _wp.load(std::memory_order_relaxed);
        _tasks[index(wp)] = std::move(task);
        _wp.store(wp + 1, std::memory_order_release);
    }
    if (wp == _wakeupConsumerAt.load(std::memory_order_relaxed)) {
        _consumerCondition.notify_one();
    }
    return task;
}

void
SingleExecutor::setTaskLimit(uint32_t taskLimit) {
    _wantedTaskLimit = std::max(vespalib::roundUp2inN(taskLimit), size_t(_watermark));
}

void
SingleExecutor::drain(Lock & lock) {
    uint64_t wp = _wp.load(std::memory_order_relaxed);
    while (numTasks() > 0) {
        _consumerCondition.notify_one();
        sleepProducer(lock, 100us, wp);
    }
}

void
SingleExecutor::wakeup() {
    _consumerCondition.notify_one();
}

SingleExecutor &
SingleExecutor::sync() {
    Lock lock(_mutex);
    uint64_t wp = _wp.load(std::memory_order_relaxed);
    while (wp > _rp.load(std::memory_order_acquire)) {
        _consumerCondition.notify_one();
        sleepProducer(lock, 100us, wp);
    }
    return *this;
}

SingleExecutor &
SingleExecutor::shutdown() {
    Lock lock(_mutex);
    _closed = true;
    return *this;
}

void
SingleExecutor::run() {
    while (!_thread.stopped()) {
        drain_tasks();
        _producerCondition.notify_all();
        _wakeupConsumerAt.store(_wp.load(std::memory_order_relaxed) + _watermark, std::memory_order_relaxed);
        Lock lock(_mutex);
        if (numTasks() <= 0) {
            _consumerCondition.wait_for(lock, _reactionTime);
        }
        _wakeupConsumerAt.store(0, std::memory_order_relaxed);
    }
}

void
SingleExecutor::drain_tasks() {
    while (numTasks() > 0) {
        run_tasks_till(_wp.load(std::memory_order_acquire));
    }
}

void
SingleExecutor::run_tasks_till(uint64_t available) {
    uint64_t consumed = _rp.load(std::memory_order_relaxed);
    uint64_t wakeupLimit = _producerNeedWakeupAt.load(std::memory_order_relaxed);
    while (consumed  < available) {
        Task::UP task = std::move(_tasks[index(consumed)]);
        task->run();
        _rp.store(++consumed, std::memory_order_release);
        if (wakeupLimit == consumed) {
            _producerCondition.notify_all();
        }
    }
}

void
SingleExecutor::wait_for_room(Lock & lock) {
    uint64_t wp = _wp.load(std::memory_order_relaxed);
    uint64_t taskLimit = _taskLimit.load(std::memory_order_relaxed);
    if (taskLimit != _wantedTaskLimit.load(std::memory_order_relaxed)) {
        drain(lock);
        _tasks = std::make_unique<Task::UP[]>(_wantedTaskLimit);
        _taskLimit = _wantedTaskLimit.load();
        taskLimit = _taskLimit;
    }
    _queueSize.add(numTasks());
    while (numTasks() >= _taskLimit.load(std::memory_order_relaxed)) {
        sleepProducer(lock, _reactionTime, wp - _watermark);
    }
}

ThreadExecutor::Stats
SingleExecutor::getStats() {
    Lock lock(_mutex);
    uint64_t accepted = _wp.load(std::memory_order_relaxed);
    Stats stats(_queueSize, (accepted - _lastAccepted), 0);
    _lastAccepted = accepted;
    _queueSize = Stats::QueueSizeT() ;
    return stats;
}


}
