// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executor_thread_service.h"
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/gate.h>

using vespalib::makeLambdaTask;
using vespalib::Executor;
using vespalib::Gate;
using vespalib::Runnable;
using vespalib::ThreadExecutor;
using vespalib::SyncableThreadExecutor;

namespace proton {

namespace {

void
sampleThreadId(std::thread::id *threadId)
{
    *threadId = std::this_thread::get_id();
}

std::thread::id
getThreadId(ThreadExecutor &executor)
{
    std::thread::id id;
    vespalib::Gate gate;
    executor.execute(makeLambdaTask([threadId = &id, &gate] {
        sampleThreadId(threadId);
        gate.countDown();
    }));
    gate.await();
    return id;
}

void
runRunnable(Runnable *runnable, Gate *gate)
{
    runnable->run();
    gate->countDown();
}

} // namespace

ExecutorThreadService::ExecutorThreadService(ThreadExecutor &executor)
    : _executor(executor),
      _threadId(getThreadId(executor))
{
}

ExecutorThreadService::~ExecutorThreadService()  = default;

void
ExecutorThreadService::run(Runnable &runnable)
{
    if (isCurrentThread()) {
        runnable.run();
    } else {
        Gate gate;
        _executor.execute(makeLambdaTask([runnablePtr=&runnable, gatePtr=&gate] { runRunnable(runnablePtr, gatePtr); }));
        gate.await();
    }
}

bool
ExecutorThreadService::isCurrentThread() const
{
    return (_threadId == std::this_thread::get_id());
}

vespalib::ExecutorStats ExecutorThreadService::getStats() {
    return _executor.getStats();
}

void ExecutorThreadService::setTaskLimit(uint32_t taskLimit) {
    _executor.setTaskLimit(taskLimit);
}

uint32_t ExecutorThreadService::getTaskLimit() const {
    return _executor.getTaskLimit();
}

void
ExecutorThreadService::wakeup() {
    _executor.wakeup();
}

SyncableExecutorThreadService::SyncableExecutorThreadService(SyncableThreadExecutor &executor)
    : _executor(executor),
      _threadId(getThreadId(executor))
{
}

SyncableExecutorThreadService::~SyncableExecutorThreadService()  = default;

void
SyncableExecutorThreadService::run(Runnable &runnable)
{
    if (isCurrentThread()) {
        runnable.run();
    } else {
        Gate gate;
        _executor.execute(makeLambdaTask([runnablePtr=&runnable, gatePtr=&gate] { runRunnable(runnablePtr, gatePtr); }));
        gate.await();
    }
}

bool
SyncableExecutorThreadService::isCurrentThread() const
{
    return (_threadId == std::this_thread::get_id());
}

vespalib::ExecutorStats
SyncableExecutorThreadService::getStats() {
    return _executor.getStats();
}

void
SyncableExecutorThreadService::setTaskLimit(uint32_t taskLimit) {
    _executor.setTaskLimit(taskLimit);
}

uint32_t
SyncableExecutorThreadService::getTaskLimit() const {
    return _executor.getTaskLimit();
}

void
SyncableExecutorThreadService::wakeup() {
    _executor.wakeup();
}

} // namespace proton
