// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executor_thread_service.h"
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/fastos/thread.h>

using vespalib::makeLambdaTask;
using vespalib::Executor;
using vespalib::Gate;
using vespalib::Runnable;
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
getThreadId(SyncableThreadExecutor &executor)
{
    std::unique_ptr<internal::ThreadId> id = std::make_unique<internal::ThreadId>();
    executor.execute(makeLambdaTask([threadId=&id->_id] { sampleThreadId(threadId);}));
    executor.sync();
    return id;
}

void
runRunnable(Runnable *runnable, Gate *gate)
{
    runnable->run();
    gate->countDown();
}

} // namespace

ExecutorThreadService::ExecutorThreadService(SyncableThreadExecutor &executor)
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

vespalib::ThreadExecutor::Stats ExecutorThreadService::getStats() {
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

} // namespace proton
