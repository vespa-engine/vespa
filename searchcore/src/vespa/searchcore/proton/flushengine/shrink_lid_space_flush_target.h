// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/flush/iflushtarget.h>

#include <atomic>

namespace search::common {
struct ICompactableLidSpace;
}

namespace proton {

/**
 * Implements a flush target that shrinks lid space in target.
 */
class ShrinkLidSpaceFlushTarget : public searchcorespi::LeafFlushTarget {
    /**
     * Task representing that shrinking has been performed.
     **/
    class Flusher;
    using ICompactableLidSpace = search::common::ICompactableLidSpace;
    using FlushStats = searchcorespi::FlushStats;
    std::shared_ptr<ICompactableLidSpace>   _target;
    std::atomic<SerialNum>                  _flushedSerialNum;
    std::atomic<vespalib::system_time::rep> _last_flush_time;
    FlushStats                              _lastStats;

    void set_flushed_serial_num(SerialNum flushed_serial_num) noexcept {
        _flushedSerialNum.store(flushed_serial_num, std::memory_order_relaxed);
    }
    void set_last_flush_time(vespalib::system_time last_flush_time) {
        _last_flush_time.store(last_flush_time.time_since_epoch().count(), std::memory_order_relaxed);
    }

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
    ShrinkLidSpaceFlushTarget(const std::string& name, Type type, Component component, SerialNum flushedSerialNum,
                              Time lastFlushTime, std::shared_ptr<ICompactableLidSpace> target);

    // Implements IFlushTarget.
    MemoryGain getApproxMemoryGain() const override;
    DiskGain getApproxDiskGain() const override;
    SerialNum getFlushedSerialNum() const override;
    Time getLastFlushTime() const override;
    Task::UP initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token) override;
    searchcorespi::FlushStats getLastFlushStats() const override;
    uint64_t getApproxBytesToWriteToDisk() const override;
};

} // namespace proton
