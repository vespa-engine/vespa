// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "indexmaintainer.h"
#include <vespa/searchcorespi/flush/iflushtarget.h>

namespace searchcorespi::index {

/**
 * Flush target for flushing a memory index in an IndexMaintainer.
 **/
class IndexFlushTarget : public LeafFlushTarget {
private:
    IndexMaintainer                   &_indexMaintainer;
    const IndexMaintainer::FlushStats  _flushStats;
    uint32_t                           _numFrozenMemoryIndexes;
    uint32_t                           _maxFrozenMemoryIndexes;
    FlushStats                         _lastStats;

public:
    explicit IndexFlushTarget(IndexMaintainer &indexMaintainer);
    IndexFlushTarget(IndexMaintainer &indexMaintainer, IndexMaintainer::FlushStats flushStats);
    ~IndexFlushTarget() override;

    // Implements IFlushTarget
    MemoryGain getApproxMemoryGain() const override;
    DiskGain   getApproxDiskGain() const override;
    SerialNum getFlushedSerialNum() const override;
    Time    getLastFlushTime() const override;

    bool needUrgentFlush() const override;
    Priority getPriority() const override { return Priority::HIGH; }

    Task::UP initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token) override;
    FlushStats getLastFlushStats() const override { return _lastStats; }
    uint64_t getApproxBytesToWriteToDisk() const override;
};

}
