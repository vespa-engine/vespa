// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_job_tracker.h"
#include <vespa/searchcorespi/flush/iflushtarget.h>

namespace proton {

/**
 * Class that tracks the start and end of an init flush in a flush target.
 * The returned flush task is also tracked.
 */
class JobTrackedFlushTarget : public searchcorespi::IFlushTarget
{
private:
    std::shared_ptr<IJobTracker>                 _tracker;
    std::shared_ptr<searchcorespi::IFlushTarget> _target;

public:
    JobTrackedFlushTarget(std::shared_ptr<IJobTracker> tracker,
                          std::shared_ptr<searchcorespi::IFlushTarget> target);
    ~JobTrackedFlushTarget() override;

    const IJobTracker &getTracker() const { return *_tracker; }
    const searchcorespi::IFlushTarget &getTarget() const { return *_target; }

    // Implements searchcorespi::IFlushTarget
    MemoryGain getApproxMemoryGain() const override {
        return _target->getApproxMemoryGain();
    }
    DiskGain getApproxDiskGain() const override {
        return _target->getApproxDiskGain();
    }
    SerialNum getFlushedSerialNum() const override {
        return _target->getFlushedSerialNum();
    }
    Time getLastFlushTime() const override {
        return _target->getLastFlushTime();
    }
    bool needUrgentFlush() const override {
        return _target->needUrgentFlush();
    }
    double get_replay_operation_cost() const override {
        return _target->get_replay_operation_cost();
    }
    Priority getPriority() const override { return _target->getPriority(); }
    searchcorespi::FlushTask::UP initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token) override;
    searchcorespi::FlushStats getLastFlushStats() const override {
        return _target->getLastFlushStats();
    }

    uint64_t getApproxBytesToWriteToDisk() const override {
        return _target->getApproxBytesToWriteToDisk();
    }
};

} // namespace proton
