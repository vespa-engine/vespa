// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/docstore/idocumentstore.h>
#include <vespa/searchcorespi/flush/iflushtarget.h>

namespace searchcorespi::index { struct IThreadService; }

namespace proton {


/**
 * This class implements the IFlushTarget interface to proxy a summary manager.
 */
class SummaryGCTarget : public searchcorespi::LeafFlushTarget {
public:
    using FlushStats = searchcorespi::FlushStats;
    using IDocumentStore = search::IDocumentStore;
    MemoryGain getApproxMemoryGain() const override;
    DiskGain getApproxDiskGain() const override;
    SerialNum getFlushedSerialNum() const override;
    Time getLastFlushTime() const override;

    Task::UP initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token) override;

    FlushStats getLastFlushStats() const override { return _lastStats; }
    uint64_t getApproxBytesToWriteToDisk() const override { return 0; }
protected:
    SummaryGCTarget(const vespalib::string &, vespalib::Executor & summaryService, IDocumentStore & docStore);
private:

    virtual size_t getBloat(const IDocumentStore & docStore) const = 0;
    virtual Task::UP create(IDocumentStore & docStore, FlushStats & stats, SerialNum currSerial) = 0;

    vespalib::Executor  &_summaryService;
    IDocumentStore      & _docStore;
    FlushStats            _lastStats;
};

/**
 * Implements target to compact away removed documents. Wasted disk space is cost factor used for prioritizing.
 */
class SummaryCompactBloatTarget : public SummaryGCTarget {
private:
    size_t getBloat(const search::IDocumentStore & docStore) const override;
    Task::UP create(IDocumentStore & docStore, FlushStats & stats, SerialNum currSerial) override;
public:
    SummaryCompactBloatTarget(vespalib::Executor & summaryService, IDocumentStore & docStore);
};

/**
 * Target to ensure bucket spread is kept low. The cost is reported as a potential gain in disk space as
 * we do not have a concept for bucket spread.
 */
class SummaryCompactSpreadTarget : public SummaryGCTarget {
private:
    size_t getBloat(const search::IDocumentStore & docStore) const override;
    Task::UP create(IDocumentStore & docStore, FlushStats & stats, SerialNum currSerial) override;
public:
    SummaryCompactSpreadTarget(vespalib::Executor & summaryService, IDocumentStore & docStore);
};

} // namespace proton

