// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summaryflushtarget.h"
#include <vespa/vespalib/util/lambdatask.h>

using search::IDocumentStore;
using search::SerialNum;
using searchcorespi::FlushStats;
using searchcorespi::IFlushTarget;

namespace proton {

namespace {

class Flusher : public searchcorespi::FlushTask {
private:
    IDocumentStore & _docStore;
    FlushStats     & _stats;
    SerialNum        _currSerial;
public:
    Flusher(IDocumentStore & docStore,
            FlushStats & stats,
            SerialNum currSerial)
        : _docStore(docStore),
          _stats(stats),
          _currSerial(currSerial)
    {
        _currSerial = _docStore.initFlush(currSerial);
    }
    void run() override {
        _docStore.flush(_currSerial);
        updateStats();
    }
    void updateStats() {
        // the target must live until this task is done (handled by flush engine).
        _stats.setPath(_docStore.getBaseDir());
    }

    SerialNum getFlushSerial() const override { return _currSerial; }
};

}

SummaryFlushTarget::SummaryFlushTarget(IDocumentStore & docStore,
                                       vespalib::Executor & summaryService)
    : LeafFlushTarget("summary.flush", Type::SYNC, Component::DOCUMENT_STORE),
      _docStore(docStore),
      _summaryService(summaryService),
      _lastStats()
{
    _lastStats.setPathElementsToLog(6);
}

IFlushTarget::MemoryGain
SummaryFlushTarget::getApproxMemoryGain() const
{
    return MemoryGain(_docStore.memoryUsed(), _docStore.memoryMeta());
}

IFlushTarget::Time
SummaryFlushTarget::getLastFlushTime() const
{
    return _docStore.getLastFlushTime();
}

SerialNum
SummaryFlushTarget::getFlushedSerialNum() const
{
    return _docStore.lastSyncToken();
}

IFlushTarget::Task::UP
SummaryFlushTarget::internalInitFlush(SerialNum currentSerial) {
    return std::make_unique<Flusher>(_docStore, _lastStats, currentSerial);
}
IFlushTarget::Task::UP
SummaryFlushTarget::initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken>)
{
    // Called by document db executor
    std::promise<Task::UP> promise;
    std::future<Task::UP> future = promise.get_future();
    _summaryService.execute(vespalib::makeLambdaTask(
                                  [&]() { promise.set_value(
                                          internalInitFlush(currentSerial));
                                  }));
    return future.get();
}

} // namespace proton
