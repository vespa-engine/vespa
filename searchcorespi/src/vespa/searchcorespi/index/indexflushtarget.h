// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "indexmaintainer.h"
#include <vespa/searchcorespi/flush/iflushtarget.h>

namespace searchcorespi {
namespace index {

/**
 * Flush target for flushing a memory index in an IndexMaintainer.
 **/
class IndexFlushTarget : public IFlushTarget {
private:
    IndexMaintainer &_indexMaintainer;
    IndexMaintainer::FlushStats _flushStats;
    uint32_t _numFrozenMemoryIndexes;
    uint32_t _maxFrozenMemoryIndexes;
    FlushStats _lastStats;

public:
    IndexFlushTarget(IndexMaintainer &indexMaintainer);
    ~IndexFlushTarget();

    // Implements IFlushTarget
    virtual MemoryGain getApproxMemoryGain() const override;
    virtual   DiskGain   getApproxDiskGain() const override;
    virtual  SerialNum getFlushedSerialNum() const override;
    virtual       Time    getLastFlushTime() const override;

    virtual bool needUrgentFlush() const override;

    virtual Task::UP initFlush(SerialNum currentSerial) override;
    virtual FlushStats getLastFlushStats() const override { return _lastStats; }
    virtual uint64_t getApproxBytesToWriteToDisk() const override;
};

}  // namespace index
}  // namespace searchcorespi

