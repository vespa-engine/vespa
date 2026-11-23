// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "shrink_lid_space_flush_target.h"

#include <vespa/opentelemetry/span_attribute_names.h>
#include <vespa/searchcore/proton/common/doc_type_and_subdb_name.h>
#include <vespa/searchcorespi/common/tracing.h>
#include <vespa/searchlib/common/i_compactable_lid_space.h>

using opentelemetry::trace::Span;
using searchcorespi::FlushStats;
using searchcorespi::FlushTask;
using searchcorespi::IFlushTarget;
using searchcorespi::LeafFlushTarget;
using searchcorespi::common::get_tracer;

namespace proton {

class ShrinkLidSpaceFlushTarget::Flusher : public FlushTask {
    ShrinkLidSpaceFlushTarget& _target;
    SerialNum                  _flushSerialNum;

public:
    Flusher(ShrinkLidSpaceFlushTarget& target, SerialNum flushSerialNum);
    void run() override;
    search::SerialNum getFlushSerial() const override;
};

ShrinkLidSpaceFlushTarget::Flusher::Flusher(ShrinkLidSpaceFlushTarget& target, SerialNum flushSerialNum)
    : FlushTask(), _target(target), _flushSerialNum(flushSerialNum) {
    /*
     * Called by document db main executor for document meta store (via derived class
     * ShrinkDocumentMetaStoreLidSpaceFlushTarget)
     * Called by document db summary executor for docstore (via derived class ShrinkSummaryLidSpaceFlushTarget)
     * Called by attribute writer executor for attribute vectors (via derived class
     * ShrinkAttributeLidSpaceFlushTarget)
     */
    _target._target->shrinkLidSpace();
}

void ShrinkLidSpaceFlushTarget::Flusher::run() {
    _target.set_flushed_serial_num(_flushSerialNum);
    _target.set_last_flush_time(vespalib::system_clock::now());
}

search::SerialNum ShrinkLidSpaceFlushTarget::Flusher::getFlushSerial() const {
    return _flushSerialNum;
}

ShrinkLidSpaceFlushTarget::ShrinkLidSpaceFlushTarget(
    const std::string& name, Type type, Component component,
    std::shared_ptr<const DocTypeAndSubDbName> doc_type_and_subdb_name_in, SerialNum flushedSerialNum,
    Time lastFlushTime, std::shared_ptr<ICompactableLidSpace> target)
    : LeafFlushTarget(name, type, component),
      _target(std::move(target)),
      _flushedSerialNum(flushedSerialNum),
      _last_flush_time(lastFlushTime.time_since_epoch().count()),
      _lastStats(),
      _doc_type_and_subdb_name(std::move(doc_type_and_subdb_name_in)) {
}

ShrinkLidSpaceFlushTarget::~ShrinkLidSpaceFlushTarget() = default;

IFlushTarget::MemoryGain ShrinkLidSpaceFlushTarget::getApproxMemoryGain() const {
    int64_t canFree = _target->getEstimatedShrinkLidSpaceGain();
    return MemoryGain(canFree, 0);
}

IFlushTarget::DiskGain ShrinkLidSpaceFlushTarget::getApproxDiskGain() const {
    return DiskGain(0, 0);
}

IFlushTarget::SerialNum ShrinkLidSpaceFlushTarget::getFlushedSerialNum() const {
    return _flushedSerialNum.load(std::memory_order_relaxed);
}

IFlushTarget::Time ShrinkLidSpaceFlushTarget::getLastFlushTime() const {
    auto ticks = _last_flush_time.load(std::memory_order_relaxed);
    return vespalib::system_time(vespalib::system_clock::duration(ticks));
}

void ShrinkLidSpaceFlushTarget::init_flush(SerialNum   currentSerial, std::shared_ptr<search::IFlushToken>,
                                           TaskPromise task_promise) {
    /*
     * Called by document db main executor for document meta store (via derived class
     * ShrinkDocumentMetaStoreLidSpaceFlushTarget)
     * Called by document db summary executor for docstore (via derived class ShrinkSummaryLidSpaceFlushTarget)
     * Called by attribute writer executor for attribute vectors (via derived class
     * ShrinkAttributeLidSpaceFlushTarget)
     */
    auto tracer = get_tracer();
    auto span =
        tracer->StartSpan(trace_span_name(),
                          {{vespa_opentelemetry::span_attribute_names::document_type,
                            _doc_type_and_subdb_name->doc_type_name().getName()},
                           {vespa_opentelemetry::span_attribute_names::subdb, _doc_type_and_subdb_name->subdb_name()},
                           {vespa_opentelemetry::span_attribute_names::serial, std::to_string(currentSerial)}});
    auto attribute_name_attr = attribute_name();
    if (!attribute_name_attr.empty()) {
        span->SetAttribute(vespa_opentelemetry::span_attribute_names::attribute, attribute_name_attr);
    }
    if (currentSerial < _flushedSerialNum.load(std::memory_order_relaxed)) {
        set_last_flush_time(vespalib::system_clock::now());
        span->AddEvent("Serial already flushed");
        span->End();
        task_promise.set_value({});
    } else if (!_target->canShrinkLidSpace()) {
        set_flushed_serial_num(currentSerial);
        set_last_flush_time(vespalib::system_clock::now());
        span->AddEvent("Cannot shrink lid space");
        span->End();
        task_promise.set_value({});
    } else {
        auto flusher = std::make_unique<Flusher>(*this, currentSerial);
        span->AddEvent("Done init flush");
        span->End();
        task_promise.set_value(std::move(flusher));
    }
}

bool ShrinkLidSpaceFlushTarget::can_flush(SerialNum current_serial) const noexcept {
    return current_serial >= _flushedSerialNum.load(std::memory_order_relaxed) && _target->canShrinkLidSpace();
}

FlushStats ShrinkLidSpaceFlushTarget::getLastFlushStats() const {
    return _lastStats;
}

uint64_t ShrinkLidSpaceFlushTarget::getApproxBytesToWriteToDisk() const {
    return 0;
}

size_t ShrinkLidSpaceFlushTarget::reserved_memory_for_flush() const noexcept {
    return 0;
}

std::chrono::steady_clock::duration ShrinkLidSpaceFlushTarget::last_flush_duration() const noexcept {
    return 200ms; // placeholder value.
}

std::chrono::steady_clock::duration ShrinkLidSpaceFlushTarget::estimated_flush_duration() const noexcept {
    return 200ms; // placeholder value.
}

[[nodiscard]] std::string_view ShrinkLidSpaceFlushTarget::attribute_name() const noexcept {
    return {};
}

} // namespace proton
