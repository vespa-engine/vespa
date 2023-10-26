// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_scheduled_executor.h"
#include <vespa/vespalib/stllike/hash_map.h>
#include <mutex>
#include <vector>

class FNET_Transport;

namespace proton {

class TimerTask;

/**
 * ScheduledExecutor is a class capable of running Tasks at a regular
 * interval. The timer can be reset to clear all tasks currently being
 * scheduled.
 */
class ScheduledExecutor : public IScheduledExecutor
{
private:
    using TaskList = vespalib::hash_map<uint64_t, std::unique_ptr<TimerTask>>;
    FNET_Transport & _transport;
    std::mutex       _lock;
    uint64_t         _nextKey;
    TaskList         _taskList;

    bool cancel(uint64_t key);
    class Registration;
public:
    /**
     * Create a new timer, capable of scheduling tasks at fixed intervals.
     */
    ScheduledExecutor(FNET_Transport & transport);

    /**
     * Destroys this timer, finishing the current task executing and then
     * finishing.
     */
    ~ScheduledExecutor() override;

    [[nodiscard]] Handle scheduleAtFixedRate(std::unique_ptr<Executor::Task> task, duration delay, duration interval) override;
};

}

