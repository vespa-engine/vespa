// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexfusiontarget.h"

#include <vespa/opentelemetry/span_attribute_names.h>
#include <vespa/searchcorespi/common/tracing.h>

#include <algorithm>
#include <cinttypes>

#include <vespa/log/log.h>
LOG_SETUP(".searchcorespi.index.indexfusiontarget");

using opentelemetry::trace::Span;
using search::SerialNum;
using searchcorespi::common::get_tracer;

namespace searchcorespi::index {

namespace {

class Fusioner : public FlushTask {
private:
    IndexMaintainer&                     _indexMaintainer;
    FlushStats&                          _stats;
    SerialNum                            _serialNum;
    std::shared_ptr<search::IFlushToken> _flush_token;
    std::shared_ptr<Span>                _span;

public:
    Fusioner(IndexMaintainer& indexMaintainer, FlushStats& stats, SerialNum serialNum,
             std::shared_ptr<search::IFlushToken> flush_token);
    ~Fusioner();

    void run() override;
    SerialNum getFlushSerial() const override;
};

Fusioner::Fusioner(IndexMaintainer& indexMaintainer, FlushStats& stats, SerialNum serialNum,
                   std::shared_ptr<search::IFlushToken> flush_token)
    : _indexMaintainer(indexMaintainer),
      _stats(stats),
      _serialNum(serialNum),
      _flush_token(std::move(flush_token)),
      _span() {
    auto tracer = get_tracer();
    _span = tracer->StartSpan(
        "Fusion of disk indexes",
        {{vespa_opentelemetry::span_attribute_names::document_type, _indexMaintainer.document_type_name()},
         {vespa_opentelemetry::span_attribute_names::serial, std::to_string(serialNum)}});
}

Fusioner::~Fusioner() = default;

void Fusioner::run() {
    _span->AddEvent("Starting background fusion");
    auto        tracer = get_tracer();
    auto        scope = tracer->WithActiveSpan(_span);
    std::string outputFusionDir = _indexMaintainer.doFusion(_serialNum, _flush_token);
    // the target must live until this task is done (handled by flush engine).
    _stats.setPath(outputFusionDir);
    _span->End();
}

SerialNum Fusioner::getFlushSerial() const {
    return 0u; // Zero means that no tls syncing is needed
}

} // namespace
IndexFusionTarget::IndexFusionTarget(IndexMaintainer& indexMaintainer)
    : LeafFlushTarget("memoryindex.fusion", Type::GC, Component::INDEX),
      _indexMaintainer(indexMaintainer),
      _fusionStats(indexMaintainer.getFusionStats()),
      _lastStats() {
    _lastStats.setPathElementsToLog(7);
    LOG(debug, "New target, Num flushed: %d, Disk usage: %" PRIu64, _fusionStats._num_unfused,
        _fusionStats._disk_usage);
}

IndexFusionTarget::~IndexFusionTarget() = default;

IFlushTarget::MemoryGain IndexFusionTarget::getApproxMemoryGain() const {
    return MemoryGain(0, 0);
}

IFlushTarget::DiskGain IndexFusionTarget::getApproxDiskGain() const {
    constexpr double max_relative_gain = 2.0;
    uint64_t         diskUsageBefore = _fusionStats._disk_usage;
    double           relative_gain =
        std::clamp<double>(0.1 * (static_cast<int>(_fusionStats._num_unfused) - 1), 0.0, max_relative_gain);
    uint64_t diskUsageGain = static_cast<uint64_t>(relative_gain * diskUsageBefore);
    diskUsageGain = std::min(diskUsageGain, diskUsageBefore);
    if (!_fusionStats._canRunFusion) {
        diskUsageGain = 0;
    }
    return DiskGain(diskUsageBefore + diskUsageGain, diskUsageBefore);
}

bool IndexFusionTarget::needUrgentFlush() const {
    bool urgent =
        (_fusionStats._num_unfused > _fusionStats._max_flushed || _indexMaintainer.urgent_disk_index_fusion()) &&
        (_fusionStats._canRunFusion);
    LOG(debug, "Num flushed: %d Urgent: %d", _fusionStats._num_unfused, urgent);
    return urgent;
}

IFlushTarget::Time IndexFusionTarget::getLastFlushTime() const {
    return vespalib::system_clock::now();
}

IFlushTarget::SerialNum IndexFusionTarget::getFlushedSerialNum() const {
    // Lack of fusion operation doesn't prevent transaction log
    // pruning.
    return _indexMaintainer.getCurrentSerialNum();
}

void IndexFusionTarget::init_flush(SerialNum serialNum, std::shared_ptr<search::IFlushToken> flush_token,
                                   TaskPromise task_promise) {
    // Called by document db main executor
    task_promise.set_value(
        std::make_unique<Fusioner>(_indexMaintainer, _lastStats, serialNum, std::move(flush_token)));
}

bool IndexFusionTarget::can_flush(SerialNum) const noexcept {
    return _fusionStats._canRunFusion;
}

uint64_t IndexFusionTarget::getApproxBytesToWriteToDisk() const {
    return _fusionStats._disk_usage;
}

size_t IndexFusionTarget::reserved_memory_for_flush() const noexcept {
    return 0;
}

std::chrono::steady_clock::duration IndexFusionTarget::last_flush_duration() const noexcept {
    return _fusionStats._last_flush_duration;
}

std::chrono::steady_clock::duration IndexFusionTarget::estimated_flush_duration() const noexcept {
    return _fusionStats._estimated_flush_duration;
}

} // namespace searchcorespi::index
