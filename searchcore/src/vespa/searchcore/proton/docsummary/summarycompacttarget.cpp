// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summarycompacttarget.h"
#include <vespa/vespalib/util/lambdatask.h>
#include <future>

using search::IDocumentStore;
using search::SerialNum;
using vespalib::makeLambdaTask;
using searchcorespi::FlushStats;
using searchcorespi::IFlushTarget;
using searchcorespi::FlushTask;

namespace proton {

namespace {

class Compacter : public FlushTask {
private:
    IDocumentStore & _docStore;
    FlushStats     & _stats;
    SerialNum        _currSerial;
    virtual void compact(IDocumentStore & docStore, SerialNum currSerial) const = 0;
public:
    Compacter(IDocumentStore & docStore, FlushStats & stats, SerialNum currSerial)
        : _docStore(docStore),
          _stats(stats),
          _currSerial(currSerial)
    {}
    void run() override {
        compact(_docStore, _currSerial);
        updateStats();
    }
    void updateStats() {
        // the target must live until this task is done (handled by flush engine).
        _stats.setPath(_docStore.getBaseDir());
    }

    SerialNum getFlushSerial() const override {
        return 0u; // Zero means no sync of transaction log is needed
    }
};

class CompactBloat : public Compacter {
public:
    CompactBloat(IDocumentStore & docStore, FlushStats & stats, SerialNum currSerial)
        : Compacter(docStore, stats, currSerial)
    {}
private:
    void compact(IDocumentStore & docStore, SerialNum currSerial) const override {
        docStore.compactBloat(currSerial);
    }
};

class CompactSpread : public Compacter {
public:
    CompactSpread(IDocumentStore & docStore, FlushStats & stats, SerialNum currSerial)
        : Compacter(docStore, stats, currSerial)
    {}
private:
    void compact(IDocumentStore & docStore, SerialNum currSerial) const override {
        docStore.compactSpread(currSerial);
    }
};

}

SummaryGCTarget::SummaryGCTarget(const vespalib::string & name, vespalib::Executor & summaryService, IDocumentStore & docStore)
    : IFlushTarget(name, Type::GC, Component::DOCUMENT_STORE),
      _summaryService(summaryService),
      _docStore(docStore),
      _lastStats()
{
    _lastStats.setPathElementsToLog(6);
}

IFlushTarget::MemoryGain
SummaryGCTarget::getApproxMemoryGain() const
{
    return MemoryGain::noGain(_docStore.memoryUsed());
}

IFlushTarget::DiskGain
SummaryGCTarget::getApproxDiskGain() const
{
    size_t total(_docStore.getDiskFootprint());
    return DiskGain(total, total - std::min(total, getBloat(_docStore)));
}

IFlushTarget::Time
SummaryGCTarget::getLastFlushTime() const
{
    return vespalib::system_clock::now();
}

SerialNum
SummaryGCTarget::getFlushedSerialNum() const
{
    return _docStore.tentativeLastSyncToken();
}

IFlushTarget::Task::UP
SummaryGCTarget::initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken>)
{
    std::promise<Task::UP> promise;
    std::future<Task::UP> future = promise.get_future();
    _summaryService.execute(makeLambdaTask([this, &promise,currentSerial]() {
        promise.set_value(create(_docStore, _lastStats, currentSerial));
    }));
    return future.get();
}

SummaryCompactBloatTarget::SummaryCompactBloatTarget(vespalib::Executor & summaryService, IDocumentStore & docStore)
    : SummaryGCTarget("summary.compact_bloat", summaryService, docStore)
{
}

size_t
SummaryCompactBloatTarget::getBloat(const search::IDocumentStore & docStore) const {
    return docStore.getDiskBloat();
}

FlushTask::UP
SummaryCompactBloatTarget::create(IDocumentStore & docStore, FlushStats & stats, SerialNum currSerial) {
    return std::make_unique<CompactBloat>(docStore, stats, currSerial);
}

SummaryCompactSpreadTarget::SummaryCompactSpreadTarget(vespalib::Executor & summaryService, IDocumentStore & docStore)
    : SummaryGCTarget("summary.compact_spread", summaryService, docStore)
{
}

size_t
SummaryCompactSpreadTarget::getBloat(const search::IDocumentStore & docStore) const {
    return docStore.getMaxSpreadAsBloat();
}

FlushTask::UP
SummaryCompactSpreadTarget::create(IDocumentStore & docStore, FlushStats & stats, SerialNum currSerial) {
    return std::make_unique<CompactSpread>(docStore, stats, currSerial);
}

} // namespace proton
