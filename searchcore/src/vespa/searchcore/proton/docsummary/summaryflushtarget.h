// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/docstore/idocumentstore.h>
#include <vespa/searchcorespi/flush/iflushtarget.h>

namespace searchcorespi::index { struct IThreadService; }
namespace proton {

/**
 * This class implements the IFlushTarget interface to proxy a summary manager.
 */
class SummaryFlushTarget : public searchcorespi::IFlushTarget {
private:
    using FlushStats = searchcorespi::FlushStats;
    search::IDocumentStore & _docStore;
    searchcorespi::index::IThreadService & _summaryService;
    FlushStats _lastStats;

    Task::UP internalInitFlush(SerialNum currentSerial);

public:
    SummaryFlushTarget(search::IDocumentStore & docStore,
                       searchcorespi::index::IThreadService & summaryService);

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

