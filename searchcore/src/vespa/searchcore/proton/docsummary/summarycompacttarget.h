// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/docstore/idocumentstore.h>
#include <vespa/searchcorespi/flush/iflushtarget.h>

namespace searchcorespi::index { struct IThreadService; }

namespace proton {


/**
 * This class implements the IFlushTarget interface to proxy a summary manager.
 */
class SummaryCompactTarget : public searchcorespi::IFlushTarget {
private:
    using FlushStats = searchcorespi::FlushStats;
    searchcorespi::index::IThreadService &_summaryService;
    search::IDocumentStore & _docStore;
    FlushStats _lastStats;

public:
    SummaryCompactTarget(searchcorespi::index::IThreadService & summaryService, search::IDocumentStore & docStore);

    // Implements IFlushTarget
    virtual MemoryGain getApproxMemoryGain() const override;
    virtual   DiskGain getApproxDiskGain() const override;
    virtual  SerialNum getFlushedSerialNum() const override;
    virtual       Time getLastFlushTime() const override;

    virtual Task::UP initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token) override;

    virtual FlushStats getLastFlushStats() const override { return _lastStats; }
    virtual uint64_t getApproxBytesToWriteToDisk() const override;
};

} // namespace proton

