// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storageframework/generic/clock/time.h>
#include <cstdint>

namespace storage::framework {

/**
 * Each thread may have different properties, as to how long they wait between
 * ticks and how long they're supposed to use processing between ticks. To be
 * able to specify this per thread, a set of properties can be set by each
 * thread.
 */
class ThreadProperties {
private:
    /**
     * Time this thread should maximum use to process before a tick is
     * registered. (Including wait time if wait time is not set)
     */
    vespalib::duration _maxProcessTime;
    /**
     * Time this thread will wait in a non-interrupted wait cycle.
     * Used in cases where a wait cycle is registered. As long as no other
     * time consuming stuff is done in a wait cycle, you can just use the
     * wait time here. The deadlock detector should add a configurable
     * global time period before flagging deadlock anyways.
     */
    vespalib::duration _waitTime;
    /**
     * Number of ticks to be done before a wait.
     */
    uint32_t _ticksBeforeWait;

public:
    ThreadProperties(vespalib::duration waitTime,
                     vespalib::duration maxProcessTime,
                     int ticksBeforeWait);

    vespalib::duration getMaxProcessTime() const { return _maxProcessTime; }
    vespalib::duration getWaitTime() const { return _waitTime; }
    int getTicksBeforeWait() const { return _ticksBeforeWait; }

    vespalib::duration getMaxCycleTime() const {
        return std::max(_maxProcessTime, _waitTime);
    }
};

}
