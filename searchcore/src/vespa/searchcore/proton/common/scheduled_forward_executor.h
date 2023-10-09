// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    class State;
    class Registration;
    using Tasks = vespalib::hash_map<uint64_t, std::unique_ptr<State>>;
    ScheduledExecutor  _scheduler;
    Executor         & _executor;
    std::mutex         _lock;
    uint64_t           _nextKey;
    Tasks              _taskList;

    bool cancel(uint64_t key);
public:
    ScheduledForwardExecutor(FNET_Transport& transport, Executor& executor);
    ~ScheduledForwardExecutor() override;
    [[nodiscard]] Handle scheduleAtFixedRate(std::unique_ptr<Executor::Task> task, duration delay, duration interval) override;
};

}

