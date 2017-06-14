// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summaryflushtarget.h"

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
    virtual void run() override {
        _docStore.flush(_currSerial);
        updateStats();
    }
    void updateStats() {
        // the target must live until this task is done (handled by flush engine).
        _stats.setPath(_docStore.getBaseDir());
    }

    virtual SerialNum
    getFlushSerial() const override
    {
        return _currSerial;
    }
};

}

SummaryFlushTarget::SummaryFlushTarget(IDocumentStore & docStore)
    : IFlushTarget("summary.flush", Type::SYNC, Component::DOCUMENT_STORE),
      _docStore(docStore),
      _lastStats()
{
    _lastStats.setPathElementsToLog(6);
}

IFlushTarget::MemoryGain
SummaryFlushTarget::getApproxMemoryGain() const
{
    return MemoryGain(_docStore.memoryUsed(), _docStore.memoryMeta());
}

IFlushTarget::DiskGain
SummaryFlushTarget::getApproxDiskGain() const
{
    return DiskGain(0, 0);
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
SummaryFlushTarget::initFlush(SerialNum currentSerial)
{
    return Task::UP(new Flusher(_docStore, _lastStats, currentSerial));
}

uint64_t
SummaryFlushTarget::getApproxBytesToWriteToDisk() const
{
    return 0;
}


} // namespace proton
