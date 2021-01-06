// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/flush/iflushtarget.h>

namespace search::common { struct ICompactableLidSpace; }

namespace proton {


/**
 * Implements a flush target that shrinks lid space in target.
 */
class ShrinkLidSpaceFlushTarget : public searchcorespi::IFlushTarget
{
    /**
     * Task representing that shrinking has been performed.
     **/
    class Flusher;
    using ICompactableLidSpace = search::common::ICompactableLidSpace;
    using FlushStats = searchcorespi::FlushStats;
    std::shared_ptr<ICompactableLidSpace> _target;
    SerialNum                             _flushedSerialNum;
    Time                                  _lastFlushTime;
    FlushStats                            _lastStats;

public:
    /**
     * Constructs a new instance of this class.
     *
     * @param name                The handler-wide unique name of this target.
     * @param type                The flush type of this target.
     * @param component           The component type of this target.
     * @param flushedSerialNum    When target shrank lid space last time
     * @param target              The target supporting lid space compaction
     */
    ShrinkLidSpaceFlushTarget(const vespalib::string &name,
                              Type type,
                              Component component,
                              SerialNum flushedSerialNum,
                              Time lastFlushTime,
                              std::shared_ptr<ICompactableLidSpace> target);

    // Implements IFlushTarget.
    MemoryGain getApproxMemoryGain() const override;
    DiskGain getApproxDiskGain() const override;
    SerialNum getFlushedSerialNum() const override;
    Time getLastFlushTime() const override;
    bool needUrgentFlush() const override;
    Task::UP initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token) override;
    searchcorespi::FlushStats getLastFlushStats() const override;
    uint64_t getApproxBytesToWriteToDisk() const override;
};

} // namespace proton
