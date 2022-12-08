// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_scheduled_executor.h"
#include "scheduledexecutor.h"

class FNET_Transport;

namespace proton {

/**
 * This class posts Tasks at a regular interval to another executor which runs them.
 */
class ScheduledForwardExecutor : public IScheduledExecutor {
private:
    ScheduledExecutor _scheduler;
    vespalib::Executor& _executor;

public:
    ScheduledForwardExecutor(FNET_Transport& transport, vespalib::Executor& executor);
    void reset();

    void scheduleAtFixedRate(std::unique_ptr<vespalib::Executor::Task> task,
                             vespalib::duration delay, vespalib::duration interval) override;

};

}

