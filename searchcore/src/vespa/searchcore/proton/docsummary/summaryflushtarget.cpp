// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summaryflushtarget.h"

#include <vespa/opentelemetry/span_attribute_names.h>
#include <vespa/searchcore/proton/common/doc_type_and_subdb_name.h>
#include <vespa/searchcorespi/common/tracing.h>
#include <vespa/vespalib/util/lambdatask.h>

using opentelemetry::trace::Span;
using search::IDocumentStore;
using search::SerialNum;
using searchcorespi::FlushStats;
using searchcorespi::IFlushTarget;
using searchcorespi::common::get_tracer;

namespace proton {

namespace {

const std::string flush_docstore_trace_span_name("Flush docstore");

class Flusher : public searchcorespi::FlushTask {
private:
    IDocumentStore&            _docStore;
    FlushStats&                _stats;
    SerialNum                  _currSerial;
    std::shared_ptr<Span>      _trace_span;
    const DocTypeAndSubDbName& _doc_type_and_subdb_name;

public:
    Flusher(IDocumentStore& docStore, FlushStats& stats, SerialNum currSerial,
            const DocTypeAndSubDbName& doc_type_and_subdb_name);
    ~Flusher() override;
    void run() override;
    void updateStats();
    SerialNum getFlushSerial() const override;
};

Flusher::Flusher(IDocumentStore& docStore, FlushStats& stats, SerialNum currSerial,
                 const DocTypeAndSubDbName& doc_type_and_subdb_name)
    : _docStore(docStore),
      _stats(stats),
      _currSerial(currSerial),
      _trace_span(),
      _doc_type_and_subdb_name(doc_type_and_subdb_name) {
    auto tracer = get_tracer();
    _trace_span = tracer->StartSpan(
        flush_docstore_trace_span_name,
        {{vespa_opentelemetry::span_attribute_names::document_type,
          _doc_type_and_subdb_name.doc_type_name().getName()},
         {vespa_opentelemetry::span_attribute_names::subdb, _doc_type_and_subdb_name.subdb_name()}});
    auto scope = tracer->WithActiveSpan(_trace_span);
    _currSerial = _docStore.initFlush(currSerial);
    _trace_span->SetAttribute(vespa_opentelemetry::span_attribute_names::serial, std::to_string(_currSerial));
    _trace_span->AddEvent("Done init flush");
}

Flusher::~Flusher() = default;

void Flusher::run() {
    _trace_span->AddEvent("Starting background flush");
    auto tracer = get_tracer();
    auto scope = tracer->WithActiveSpan(_trace_span);
    _docStore.flush(_currSerial);
    updateStats();
    _trace_span->End();
}

void Flusher::updateStats() {
    // the target must live until this task is done (handled by flush engine).
    _stats.setPath(_docStore.getBaseDir());
}

SerialNum Flusher::getFlushSerial() const {
    return _currSerial;
}

} // namespace

SummaryFlushTarget::SummaryFlushTarget(IDocumentStore& docStore, vespalib::Executor& summaryService,
                                       std::shared_ptr<const DocTypeAndSubDbName> doc_type_and_subdb_name_in)
    : LeafFlushTarget("summary.flush", Type::SYNC, Component::DOCUMENT_STORE),
      _docStore(docStore),
      _summaryService(summaryService),
      _lastStats(),
      _doc_type_and_subdb_name(std::move(doc_type_and_subdb_name_in)) {
    _lastStats.setPathElementsToLog(6);
}

IFlushTarget::MemoryGain SummaryFlushTarget::getApproxMemoryGain() const {
    return MemoryGain(_docStore.memoryUsed(), _docStore.memoryMeta());
}

IFlushTarget::Time SummaryFlushTarget::getLastFlushTime() const {
    return _docStore.getLastFlushTime();
}

SerialNum SummaryFlushTarget::getFlushedSerialNum() const {
    return _docStore.lastSyncToken();
}

void SummaryFlushTarget::internal_init_flush(SerialNum currentSerial, TaskPromise task_promise) {
    // called by document db summary executor
    task_promise.set_value(
        std::make_unique<Flusher>(_docStore, _lastStats, currentSerial, *_doc_type_and_subdb_name));
}

void SummaryFlushTarget::init_flush(SerialNum   currentSerial, std::shared_ptr<search::IFlushToken>,
                                    TaskPromise task_promise) {
    // Called by document db main executor
    auto        tracer = get_tracer();
    auto        parent_span = tracer->GetCurrentSpan();
    TaskPromise proxied_task_promise;
    auto        future_task = proxied_task_promise.get_future();
    _summaryService.execute(
        vespalib::makeLambdaTask([&, proxied_task_promise(std::move(proxied_task_promise)), tracer(std::move(tracer)),
                                  parent_span(std::move(parent_span))]() mutable {
            auto scope = tracer->WithActiveSpan(parent_span);
            internal_init_flush(currentSerial, std::move(proxied_task_promise));
        }));
    task_promise.set_value(future_task.get());
}

bool SummaryFlushTarget::can_flush(SerialNum current_serial) const noexcept {
    return current_serial > _docStore.lastSyncToken();
}

size_t SummaryFlushTarget::reserved_memory_for_flush() const noexcept {
    return 0;
}

std::chrono::steady_clock::duration SummaryFlushTarget::last_flush_duration() const noexcept {
    return 2s; // placeholder value.
}

std::chrono::steady_clock::duration SummaryFlushTarget::estimated_flush_duration() const noexcept {
    return 2s; // placeholder value.
}

} // namespace proton
