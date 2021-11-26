// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executor_thread_service.h"
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/fastos/thread.h>

using vespalib::makeLambdaTask;
using vespalib::Executor;
using vespalib::Gate;
using vespalib::Runnable;
using vespalib::ThreadExecutor;
using vespalib::SyncableThreadExecutor;

namespace proton {

namespace internal {

struct ThreadId {
    FastOS_ThreadId _id;
};
}

namespace {

void
sampleThreadId(FastOS_ThreadId *threadId)
{
    *threadId = FastOS_Thread::GetCurrentThreadId();
}

std::unique_ptr<internal::ThreadId>
getThreadId(ThreadExecutor &executor)
{
    std::unique_ptr<internal::ThreadId> id = std::make_unique<internal::ThreadId>();
    vespalib::Gate gate;
    executor.execute(makeLambdaTask([threadId=&id->_id, &gate] {
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
    FastOS_ThreadId currentThreadId = FastOS_Thread::GetCurrentThreadId();
    return FastOS_Thread::CompareThreadIds(_threadId->_id, currentThreadId);
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
    FastOS_ThreadId currentThreadId = FastOS_Thread::GetCurrentThreadId();
    return FastOS_Thread::CompareThreadIds(_threadId->_id, currentThreadId);
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
