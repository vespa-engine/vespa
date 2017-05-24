// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/docstore/idocumentstore.h>
#include <vespa/searchcorespi/flush/iflushtarget.h>

namespace proton {

/**
 * This class implements the IFlushTarget interface to proxy a summary manager.
 */
class SummaryFlushTarget : public searchcorespi::IFlushTarget {
private:
    using FlushStats = searchcorespi::FlushStats;
    search::IDocumentStore & _docStore;
    FlushStats _lastStats;

public:
    SummaryFlushTarget(search::IDocumentStore & docStore);

    // Implements IFlushTarget
    virtual MemoryGain getApproxMemoryGain() const override;
    virtual   DiskGain getApproxDiskGain() const override;
    virtual  SerialNum getFlushedSerialNum() const override;
    virtual       Time getLastFlushTime() const override;

    virtual Task::UP initFlush(SerialNum currentSerial) override;

    virtual FlushStats getLastFlushStats() const override { return _lastStats; }
    virtual uint64_t getApproxBytesToWriteToDisk() const override;
};

} // namespace proton

