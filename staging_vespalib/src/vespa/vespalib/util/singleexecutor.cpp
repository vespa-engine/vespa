// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "singleexecutor.h"
#include <vespa/vespalib/util/time.h>

namespace vespalib {

SingleExecutor::SingleExecutor(uint32_t taskLimit)
    : _taskLimit(vespalib::roundUp2inN(taskLimit)),
      _wantedTaskLimit(_taskLimit.load()),
      _rp(0),
      _tasks(std::make_unique<Task::UP[]>(_taskLimit)),
      _consumerMutex(),
      _consumerCondition(),
      _producerMutex(),
      _producerCondition(),
      _thread(*this),
      _lastAccepted(0),
      _maxPending(0),
      _wakeupConsumerAt(0),
      _producerNeedWakeup(false),
      _wp(0)
{
    _thread.start();
}
SingleExecutor::~SingleExecutor() {
    sync();
    _thread.stop().join();
}

size_t
SingleExecutor::getNumThreads() const {
    return 1;
}

uint64_t
SingleExecutor::addTask(Task::UP task) {
    Lock guard(_producerMutex);
    wait_for_room(guard);
    uint64_t wp = _wp.load(std::memory_order_relaxed);
    _tasks[index(wp)] = std::move(task);
    _wp.store(wp + 1, std::memory_order_release);
    return wp;
}

void
SingleExecutor::sleepProducer(Lock & lock, duration maxWaitTime) {
    _producerNeedWakeup.store(true, std::memory_order_relaxed);
    _producerCondition.wait_for(lock, maxWaitTime);
    _producerNeedWakeup.store(false, std::memory_order_relaxed);
}

Executor::Task::UP
SingleExecutor::execute(Task::UP task) {
    uint64_t wp = addTask(std::move(task));
    if (wp == _wakeupConsumerAt.load(std::memory_order_relaxed)) {
        _consumerCondition.notify_one();
    }
    return task;
}

void
SingleExecutor::setTaskLimit(uint32_t taskLimit) {
    _wantedTaskLimit = vespalib::roundUp2inN(taskLimit);
}

SingleExecutor &
SingleExecutor::sync() {
    uint64_t wp = _wp.load(std::memory_order_relaxed);
    while (wp > _rp.load(std::memory_order_acquire)) {
        _consumerCondition.notify_one();
        Lock lock(_producerMutex);
        sleepProducer(lock, 100us);
    }
    return *this;
}

void
SingleExecutor::run() {
    while (!_thread.stopped()) {
        drain_tasks();
        _producerCondition.notify_all();
        _wakeupConsumerAt.store(_wp.load(std::memory_order_relaxed) + (_taskLimit.load(std::memory_order_relaxed) / 4), std::memory_order_relaxed);
        Lock lock(_consumerMutex);
        _consumerCondition.wait_for(lock, 10ms);
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
    uint64_t left = available - consumed;
    if (_maxPending.load(std::memory_order_relaxed) < left) {
        _maxPending.store(left, std::memory_order_relaxed);
    }
    uint64_t wakeupLimit = _producerNeedWakeup.load(std::memory_order_relaxed)
            ? (available - (left / 4))
            : 0;
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
SingleExecutor::wait_for_room(Lock & producerGuard) {
    if (_taskLimit.load(std::memory_order_relaxed) != _wantedTaskLimit.load(std::memory_order_relaxed)) {
        sync();
        _tasks = std::make_unique<Task::UP[]>(_wantedTaskLimit);
        _taskLimit = _wantedTaskLimit.load();
    }
    while (numTasks() >= _taskLimit.load(std::memory_order_relaxed)) {
        sleepProducer(producerGuard, 10ms);
    }
}

ThreadExecutor::Stats
SingleExecutor::getStats() {
    uint64_t accepted = _wp.load(std::memory_order_relaxed);
    Stats stats(_maxPending, (accepted - _lastAccepted), 0);
    _lastAccepted = accepted;
    _maxPending = 0;
    return stats;
}


}
