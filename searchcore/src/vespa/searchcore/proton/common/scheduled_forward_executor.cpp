// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "scheduled_forward_executor.h"
#include <vespa/vespalib/util/lambdatask.h>

using vespalib::Executor;
using vespalib::makeLambdaTask;

namespace proton {

ScheduledForwardExecutor::ScheduledForwardExecutor(FNET_Transport& transport,
                                                   vespalib::Executor& executor)
    : _scheduler(transport),
      _executor(executor)
{
}

void
ScheduledForwardExecutor::reset()
{
    _scheduler.reset();
}

void
ScheduledForwardExecutor::scheduleAtFixedRate(vespalib::Executor::Task::UP task,
                                              vespalib::duration delay, vespalib::duration interval)
{
    _scheduler.scheduleAtFixedRate(makeLambdaTask([&, my_task = std::move(task)]() {
        _executor.execute(makeLambdaTask([&]() {
            my_task->run();
        }));
    }), delay, interval);
}

}
