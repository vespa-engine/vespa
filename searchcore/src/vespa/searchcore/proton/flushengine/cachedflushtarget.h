// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    bool              _needUrgentFlush;
    uint64_t          _approxBytesToWriteToDisk;

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
    virtual MemoryGain getApproxMemoryGain() const override { return _memoryGain; }
    virtual   DiskGain   getApproxDiskGain() const override { return _diskGain; }
    virtual  SerialNum getFlushedSerialNum() const override { return _flushedSerialNum; }
    virtual       Time    getLastFlushTime() const override { return _lastFlushTime; }
    virtual       bool     needUrgentFlush() const override { return _needUrgentFlush; }

    virtual Task::UP initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token) override { return _target->initFlush(currentSerial, std::move(flush_token)); }
    virtual FlushStats getLastFlushStats() const override { return _target->getLastFlushStats(); }

    virtual uint64_t getApproxBytesToWriteToDisk() const override;
};

} // namespace proton

