// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summarycompacttarget.h"
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/searchcorespi/index/i_thread_service.h>
#include <future>

using search::IDocumentStore;
using search::SerialNum;
using vespalib::makeLambdaTask;
using searchcorespi::FlushStats;
using searchcorespi::IFlushTarget;

namespace proton {

namespace {

class Compacter : public searchcorespi::FlushTask {
private:
    IDocumentStore & _docStore;
    FlushStats     & _stats;
    SerialNum        _currSerial;
public:
    Compacter(IDocumentStore & docStore, FlushStats & stats, SerialNum currSerial) :
        _docStore(docStore), _stats(stats), _currSerial(currSerial) {}
    void run() override {
        _docStore.compact(_currSerial);
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

}

SummaryCompactTarget::SummaryCompactTarget(searchcorespi::index::IThreadService & summaryService, IDocumentStore & docStore)
    : IFlushTarget("summary.compact", Type::GC, Component::DOCUMENT_STORE),
      _summaryService(summaryService),
      _docStore(docStore),
      _lastStats()
{
    _lastStats.setPathElementsToLog(6);
}

IFlushTarget::MemoryGain
SummaryCompactTarget::getApproxMemoryGain() const
{
    return MemoryGain::noGain(_docStore.memoryUsed());
}

IFlushTarget::DiskGain
SummaryCompactTarget::getApproxDiskGain() const
{
    uint64_t total(_docStore.getDiskFootprint());
    return DiskGain(total, total - std::min(total, static_cast<uint64_t>(_docStore.getMaxCompactGain())));
}

IFlushTarget::Time
SummaryCompactTarget::getLastFlushTime() const
{
    return vespalib::system_clock::now();
}

SerialNum
SummaryCompactTarget::getFlushedSerialNum() const
{
    return _docStore.tentativeLastSyncToken();
}

IFlushTarget::Task::UP
SummaryCompactTarget::initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken>)
{
    std::promise<Task::UP> promise;
    std::future<Task::UP> future = promise.get_future();
    _summaryService.execute(makeLambdaTask([&]() { promise.set_value(std::make_unique<Compacter>(_docStore, _lastStats, currentSerial)); }));
    return future.get();
}

uint64_t
SummaryCompactTarget::getApproxBytesToWriteToDisk() const
{
    return 0;
}

} // namespace proton
