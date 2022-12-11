// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "scheduled_forward_executor.h"
#include <vespa/vespalib/util/lambdatask.h>

using vespalib::makeLambdaTask;

namespace proton {

ScheduledForwardExecutor::ScheduledForwardExecutor(FNET_Transport& transport,
                                                   Executor& executor)
    : _scheduler(transport),
      _executor(executor)
{
}

void
ScheduledForwardExecutor::reset()
{
    _scheduler.reset();
}

IScheduledExecutor::Handle
ScheduledForwardExecutor::scheduleAtFixedRate(Executor::Task::UP task,
                                              duration delay, duration interval)
{
    return _scheduler.scheduleAtFixedRate(makeLambdaTask([&, my_task = std::move(task)]() {
        _executor.execute(makeLambdaTask([&]() {
            my_task->run();
        }));
    }), delay, interval);
}

}
