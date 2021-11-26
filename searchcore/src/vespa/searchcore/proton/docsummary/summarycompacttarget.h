// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    vespalib::Executor &_summaryService;
    search::IDocumentStore & _docStore;
    FlushStats _lastStats;

public:
    SummaryCompactTarget(vespalib::Executor & summaryService, search::IDocumentStore & docStore);

    MemoryGain getApproxMemoryGain() const override;
    DiskGain getApproxDiskGain() const override;
    SerialNum getFlushedSerialNum() const override;
    Time getLastFlushTime() const override;

    Task::UP initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token) override;

    FlushStats getLastFlushStats() const override { return _lastStats; }
    uint64_t getApproxBytesToWriteToDisk() const override { return 0; }
};

} // namespace proton

