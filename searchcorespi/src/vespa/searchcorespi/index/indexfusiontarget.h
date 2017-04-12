// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/flush/iflushtarget.h>
#include "indexmaintainer.h"

namespace searchcorespi {
namespace index {

/**
 * Flush target for doing fusion on disk indexes in an IndexMaintainer.
 **/
class IndexFusionTarget : public IFlushTarget {
private:
    IndexMaintainer &_indexMaintainer;
    IndexMaintainer::FusionStats _fusionStats;
    FlushStats _lastStats;

public:
    IndexFusionTarget(IndexMaintainer &indexMaintainer);

    // Implements IFlushTarget
    virtual MemoryGain getApproxMemoryGain() const override;
    virtual   DiskGain   getApproxDiskGain() const override;
    virtual  SerialNum getFlushedSerialNum() const override;
    virtual       Time    getLastFlushTime() const override;
    virtual bool           needUrgentFlush() const override;

    virtual Task::UP initFlush(SerialNum currentSerial) override;
    virtual FlushStats getLastFlushStats() const override { return _lastStats; }
    virtual uint64_t getApproxBytesToWriteToDisk() const override;
};

}  // namespace index
}  // namespace searchcorespi

