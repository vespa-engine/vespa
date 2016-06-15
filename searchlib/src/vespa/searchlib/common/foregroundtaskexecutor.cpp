// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".common.foregroundtaskexecutor");

#include "foregroundtaskexecutor.h"
#include <vespa/vespalib/util/threadstackexecutor.h>

using vespalib::ThreadStackExecutor;

namespace search
{

namespace
{

constexpr uint32_t stackSize = 128 * 1024;

}


ForegroundTaskExecutor::ForegroundTaskExecutor()
{
}

ForegroundTaskExecutor::~ForegroundTaskExecutor()
{
}


void
ForegroundTaskExecutor::executeTask(uint64_t id,
                                   vespalib::Executor::Task::UP task)
{
    (void) id;
    task->run();
}


void
ForegroundTaskExecutor::sync()
{
}


} // namespace search
