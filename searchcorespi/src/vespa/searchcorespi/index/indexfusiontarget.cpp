// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexfusiontarget.h"

#include <vespa/log/log.h>
LOG_SETUP(".searchcorespi.index.indexfusiontarget");

namespace searchcorespi::index {

using search::SerialNum;
namespace {

class Fusioner : public FlushTask {
private:
    IndexMaintainer &_indexMaintainer;
    FlushStats      &_stats;
    SerialNum        _serialNum;
    std::shared_ptr<search::IFlushToken> _flush_token;
public:
    Fusioner(IndexMaintainer &indexMaintainer, FlushStats &stats, SerialNum serialNum, std::shared_ptr<search::IFlushToken> flush_token) :
        _indexMaintainer(indexMaintainer),
        _stats(stats),
        _serialNum(serialNum),
        _flush_token(std::move(flush_token))
    {}

    void run() override {
        vespalib::string outputFusionDir = _indexMaintainer.doFusion(_serialNum, _flush_token);
        // the target must live until this task is done (handled by flush engine).
        _stats.setPath(outputFusionDir);
    }

    SerialNum getFlushSerial() const override {
        return 0u; // Zero means that no tls syncing is needed
    }
};

}
IndexFusionTarget::IndexFusionTarget(IndexMaintainer &indexMaintainer)
    : IFlushTarget("memoryindex.fusion", Type::GC, Component::INDEX),
      _indexMaintainer(indexMaintainer),
      _fusionStats(indexMaintainer.getFusionStats()),
      _lastStats()
{
    _lastStats.setPathElementsToLog(7);
    LOG(debug, "New target, Num flushed: %d, Disk usage: %" PRIu64, _fusionStats.numUnfused, _fusionStats.diskUsage);
}

IndexFusionTarget::~IndexFusionTarget() = default;

IFlushTarget::MemoryGain
IndexFusionTarget::getApproxMemoryGain() const
{
    return MemoryGain(0, 0);
}

IFlushTarget::DiskGain
IndexFusionTarget::getApproxDiskGain() const
{
    uint64_t diskUsageBefore = _fusionStats.diskUsage;
    uint64_t diskUsageGain = static_cast<uint64_t>((0.1 * (diskUsageBefore * std::max(0,static_cast<int>(_fusionStats.numUnfused - 1)))));
    diskUsageGain = std::min(diskUsageGain, diskUsageBefore);
    if (!_fusionStats._canRunFusion)
        diskUsageGain = 0;
    return DiskGain(diskUsageBefore, diskUsageBefore - diskUsageGain);
}

bool
IndexFusionTarget::needUrgentFlush() const
{
    bool urgent = (_fusionStats.numUnfused > _fusionStats.maxFlushed) && (_fusionStats._canRunFusion);
    LOG(debug, "Num flushed: %d Urgent: %d", _fusionStats.numUnfused, urgent);
    return urgent;
}

IFlushTarget::Time
IndexFusionTarget::getLastFlushTime() const
{
    return vespalib::system_clock::now();
}

IFlushTarget::SerialNum
IndexFusionTarget::getFlushedSerialNum() const
{
    // Lack of fusion operation doesn't prevent transaction log
    // pruning.
    return _indexMaintainer.getCurrentSerialNum();
}

IFlushTarget::Task::UP
IndexFusionTarget::initFlush(SerialNum serialNum, std::shared_ptr<search::IFlushToken> flush_token)
{
    return std::make_unique<Fusioner>(_indexMaintainer, _lastStats, serialNum, std::move(flush_token));
}

uint64_t
IndexFusionTarget::getApproxBytesToWriteToDisk() const
{
    return _fusionStats.diskUsage;
}

}
