// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    vespalib::Executor     & _summaryService;
    FlushStats               _lastStats;

    Task::UP internalInitFlush(SerialNum currentSerial);

public:
    SummaryFlushTarget(search::IDocumentStore & docStore,
                       vespalib::Executor & summaryService);

    MemoryGain getApproxMemoryGain() const override;
    DiskGain getApproxDiskGain() const override { return DiskGain(0, 0); }
    SerialNum getFlushedSerialNum() const override;
    Time getLastFlushTime() const override;

    Task::UP initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token) override;

    FlushStats getLastFlushStats() const override { return _lastStats; }
    uint64_t getApproxBytesToWriteToDisk() const override { return 0; }
};

} // namespace proton

