// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executor_thread_service.h"
#include <vespa/vespalib/util/closuretask.h>
#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/util/sync.h>

using vespalib::makeClosure;
using vespalib::makeTask;
using vespalib::Executor;
using vespalib::Gate;
using vespalib::Runnable;
using vespalib::ThreadStackExecutorBase;

namespace proton {

namespace {

void
sampleThreadId(FastOS_ThreadId *threadId)
{
    *threadId = FastOS_Thread::GetCurrentThreadId();
}

FastOS_ThreadId
getThreadId(ThreadStackExecutorBase &executor)
{
    FastOS_ThreadId id;
    executor.execute(makeTask(makeClosure(&sampleThreadId, &id)));
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

ExecutorThreadService::ExecutorThreadService(ThreadStackExecutorBase &executor)
    : _executor(executor),
      _threadId(getThreadId(executor))
{
}

void
ExecutorThreadService::run(Runnable &runnable)
{
    if (isCurrentThread()) {
        runnable.run();
    } else {
        Gate gate;
        _executor.execute(makeTask(makeClosure(&runRunnable, &runnable, &gate)));
        gate.await();
    }
}

bool
ExecutorThreadService::isCurrentThread() const
{
    FastOS_ThreadId currentThreadId = FastOS_Thread::GetCurrentThreadId();
    return FastOS_Thread::CompareThreadIds(_threadId, currentThreadId);
}

} // namespace proton
