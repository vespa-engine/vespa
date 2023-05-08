// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "indexmaintainer.h"
#include <vespa/searchcorespi/flush/iflushtarget.h>

namespace searchcorespi::index {

/**
 * Flush target for doing fusion on disk indexes in an IndexMaintainer.
 **/
class IndexFusionTarget : public LeafFlushTarget {
private:
    IndexMaintainer &_indexMaintainer;
    IndexMaintainer::FusionStats _fusionStats;
    FlushStats _lastStats;

public:
    IndexFusionTarget(IndexMaintainer &indexMaintainer);
    ~IndexFusionTarget() override;

    // Implements IFlushTarget
    MemoryGain getApproxMemoryGain() const override;
    DiskGain   getApproxDiskGain() const override;
    SerialNum getFlushedSerialNum() const override;
    Time    getLastFlushTime() const override;
    bool           needUrgentFlush() const override;

    Task::UP initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token) override;
    FlushStats getLastFlushStats() const override { return _lastStats; }
    uint64_t getApproxBytesToWriteToDisk() const override;
};

}
