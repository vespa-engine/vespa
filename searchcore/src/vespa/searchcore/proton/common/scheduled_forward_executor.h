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
    ScheduledExecutor  _scheduler;
    Executor         & _executor;

public:
    ScheduledForwardExecutor(FNET_Transport& transport, Executor& executor);
    void reset();

    [[nodiscard]] Handle scheduleAtFixedRate(std::unique_ptr<Executor::Task> task, duration delay, duration interval) override;
};

}

