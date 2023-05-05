// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/flush/iflushtarget.h>

namespace proton {

/**
 * Implements a flush target that caches the flushable memory and flush cost of
 * a decorated target. This is used by the flush engine to avoid recalculating
 * these during selection of flush target.
 */
class CachedFlushTarget : public searchcorespi::IFlushTarget {
private:
    using FlushStats = searchcorespi::FlushStats;
    using IFlushTarget = searchcorespi::IFlushTarget;
    IFlushTarget::SP  _target;
    SerialNum         _flushedSerialNum;
    Time              _lastFlushTime;
    MemoryGain        _memoryGain;
    DiskGain          _diskGain;
    uint64_t          _approxBytesToWriteToDisk;
    double            _replay_operation_cost;
    bool              _needUrgentFlush;
    Priority          _priority;

public:
    /**
     * Constructs a new instance of this class. This will immediately call
     * getFlushableMemory(), getFlushCost() and getLowSerialNum() on the
     * argument target.
     *
     * @param target The target to decorate.
     */
    CachedFlushTarget(const IFlushTarget::SP &target);

    /**
     * Returns the decorated flush target. This should not be used for anything
     * but testing, as invoking a method on the returned target beats the
     * purpose of decorating it.
     *
     * @return The decorated flush target.
     */
    const IFlushTarget::SP & getFlushTarget() { return _target; }

    // Implements IFlushTarget.
    MemoryGain getApproxMemoryGain() const override { return _memoryGain; }
    DiskGain   getApproxDiskGain() const override { return _diskGain; }
    SerialNum getFlushedSerialNum() const override { return _flushedSerialNum; }
    Time    getLastFlushTime() const override { return _lastFlushTime; }
    bool     needUrgentFlush() const override { return _needUrgentFlush; }
    Priority getPriority() const override { return _priority; }
    double get_replay_operation_cost() const override { return _replay_operation_cost; }

    Task::UP initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token) override {
        return _target->initFlush(currentSerial, std::move(flush_token));
    }
    FlushStats getLastFlushStats() const override { return _target->getLastFlushStats(); }

    uint64_t getApproxBytesToWriteToDisk() const override { return _approxBytesToWriteToDisk; }
};

} // namespace proton

