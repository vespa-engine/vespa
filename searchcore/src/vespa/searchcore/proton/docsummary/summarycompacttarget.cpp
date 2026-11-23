// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summarycompacttarget.h"

#include <vespa/opentelemetry/span_attribute_names.h>
#include <vespa/searchcore/proton/common/doc_type_and_subdb_name.h>
#include <vespa/searchcorespi/common/tracing.h>
#include <vespa/vespalib/util/lambdatask.h>

#include <future>

using opentelemetry::trace::Span;
using search::IDocumentStore;
using search::SerialNum;
using searchcorespi::FlushStats;
using searchcorespi::FlushTask;
using searchcorespi::IFlushTarget;
using searchcorespi::common::get_tracer;
using vespalib::makeLambdaTask;

namespace proton {

namespace {

const std::string compact_summary_bloat_trace_span_name("Compact summary bloat");
const std::string compact_summary_bucket_spread_trace_span_name("Compact summary bucket spread");
const std::string compact_summary_bloat_name("summary.compact_bloat");
const std::string compact_summary_bucket_spread_name("summary.compact_spread");

class Compacter : public FlushTask {
private:
    IDocumentStore&       _docStore;
    FlushStats&           _stats;
    SerialNum             _currSerial;
    std::shared_ptr<Span> _trace_span;

private:
    virtual void compact(IDocumentStore& docStore, SerialNum currSerial) const = 0;

public:
    Compacter(IDocumentStore& docStore, FlushStats& stats, SerialNum currSerial,
              const DocTypeAndSubDbName& doc_type_and_subdb_name, std::string_view trace_span_name);
    ~Compacter() override;
    void run() override;
    void updateStats();
    SerialNum getFlushSerial() const override;
};

Compacter::Compacter(IDocumentStore& docStore, FlushStats& stats, SerialNum currSerial,
                     const DocTypeAndSubDbName& doc_type_and_subdb_name, std::string_view trace_span_name)
    : _docStore(docStore), _stats(stats), _currSerial(currSerial), _trace_span() {
    auto tracer = get_tracer();
    _trace_span = tracer->StartSpan(
        trace_span_name, {{vespa_opentelemetry::span_attribute_names::document_type,
                           doc_type_and_subdb_name.doc_type_name().getName()},
                          {vespa_opentelemetry::span_attribute_names::subdb, doc_type_and_subdb_name.subdb_name()},
                          {vespa_opentelemetry::span_attribute_names::serial, std::to_string(currSerial)}});
}

Compacter::~Compacter() = default;

void Compacter::run() {
    _trace_span->AddEvent("Starting background compact");
    auto tracer = get_tracer();
    auto scope = tracer->WithActiveSpan(_trace_span);
    compact(_docStore, _currSerial);
    updateStats();
    _trace_span->End();
}

void Compacter::updateStats() {
    // the target must live until this task is done (handled by flush engine).
    _stats.setPath(_docStore.getBaseDir());
}

SerialNum Compacter::getFlushSerial() const {
    return 0u; // Zero means no sync of transaction log is needed
}

class CompactBloat : public Compacter {
public:
    CompactBloat(IDocumentStore& docStore, FlushStats& stats, SerialNum currSerial,
                 const DocTypeAndSubDbName& doc_type_and_subdb_name);
    ~CompactBloat() override;

private:
    void compact(IDocumentStore& docStore, SerialNum currSerial) const override;
};

CompactBloat::CompactBloat(IDocumentStore& docStore, FlushStats& stats, SerialNum currSerial,
                           const DocTypeAndSubDbName& doc_type_and_subdb_name)
    : Compacter(docStore, stats, currSerial, doc_type_and_subdb_name, compact_summary_bloat_trace_span_name) {
}

CompactBloat::~CompactBloat() = default;

void CompactBloat::compact(IDocumentStore& docStore, SerialNum currSerial) const {
    docStore.compactBloat(currSerial);
}

class CompactSpread : public Compacter {
public:
    CompactSpread(IDocumentStore& docStore, FlushStats& stats, SerialNum currSerial,
                  const DocTypeAndSubDbName& doc_type_and_subdb_name);
    ~CompactSpread() override;

private:
    void compact(IDocumentStore& docStore, SerialNum currSerial) const override;
};

CompactSpread::CompactSpread(IDocumentStore& docStore, FlushStats& stats, SerialNum currSerial,
                             const DocTypeAndSubDbName& doc_type_and_subdb_name)
    : Compacter(docStore, stats, currSerial, doc_type_and_subdb_name, compact_summary_bucket_spread_trace_span_name) {
}

CompactSpread::~CompactSpread() = default;

void CompactSpread::compact(IDocumentStore& docStore, SerialNum currSerial) const {
    docStore.compactSpread(currSerial);
}

} // namespace

SummaryGCTarget::SummaryGCTarget(const std::string& name, vespalib::Executor& summaryService,
                                 IDocumentStore&                            docStore,
                                 std::shared_ptr<const DocTypeAndSubDbName> doc_type_and_subdb_name_in)
    : LeafFlushTarget(name, Type::GC, Component::DOCUMENT_STORE),
      _summaryService(summaryService),
      _docStore(docStore),
      _lastStats(),
      _doc_type_and_subdb_name(std::move(doc_type_and_subdb_name_in)) {
    _lastStats.setPathElementsToLog(6);
}

IFlushTarget::MemoryGain SummaryGCTarget::getApproxMemoryGain() const {
    return MemoryGain::noGain(_docStore.memoryUsed());
}

IFlushTarget::DiskGain SummaryGCTarget::getApproxDiskGain() const {
    size_t total(_docStore.getDiskFootprint());
    return DiskGain(total, total - std::min(total, getBloat(_docStore)));
}

IFlushTarget::Time SummaryGCTarget::getLastFlushTime() const {
    return vespalib::system_clock::now();
}

SerialNum SummaryGCTarget::getFlushedSerialNum() const {
    return _docStore.tentativeLastSyncToken();
}

void SummaryGCTarget::init_flush(SerialNum   currentSerial, std::shared_ptr<search::IFlushToken>,
                                 TaskPromise task_promise) {
    // Called by document db main executor
    auto        tracer = get_tracer();
    auto        parent_span = tracer->GetCurrentSpan();
    TaskPromise proxied_task_promise;
    auto        future_task = proxied_task_promise.get_future();
    _summaryService.execute(
        makeLambdaTask([this, currentSerial, proxied_task_promise(std::move(proxied_task_promise)),
                        tracer(std::move(tracer)), parent_span(std::move(parent_span))]() mutable {
            auto scope = tracer->WithActiveSpan(parent_span);
            create(_docStore, _lastStats, currentSerial, std::move(proxied_task_promise));
        }));
    task_promise.set_value(future_task.get());
}

bool SummaryGCTarget::can_flush(SerialNum) const noexcept {
    return true;
}

size_t SummaryGCTarget::reserved_memory_for_flush() const noexcept {
    return _docStore.max_file_size();
}

std::chrono::steady_clock::duration SummaryGCTarget::last_flush_duration() const noexcept {
    return 10s; // placeholder value.
}

std::chrono::steady_clock::duration SummaryGCTarget::estimated_flush_duration() const noexcept {
    return 10s; // placeholder value.
}

SummaryCompactBloatTarget::SummaryCompactBloatTarget(
    vespalib::Executor& summaryService, IDocumentStore& docStore,
    std::shared_ptr<const DocTypeAndSubDbName> doc_type_and_subdb_name_in)
    : SummaryGCTarget(compact_summary_bloat_name, summaryService, docStore, std::move(doc_type_and_subdb_name_in)) {
}

size_t SummaryCompactBloatTarget::getBloat(const search::IDocumentStore& docStore) const {
    return docStore.getDiskBloat();
}

void SummaryCompactBloatTarget::create(IDocumentStore& docStore, FlushStats& stats, SerialNum currSerial,
                                       TaskPromise task_promise) {
    // called by document db summary executor
    auto task = std::make_unique<CompactBloat>(docStore, stats, currSerial, *_doc_type_and_subdb_name);
    task_promise.set_value(std::move(task));
}

SummaryCompactSpreadTarget::SummaryCompactSpreadTarget(
    vespalib::Executor& summaryService, IDocumentStore& docStore,
    std::shared_ptr<const DocTypeAndSubDbName> doc_type_and_subdb_name_in)
    : SummaryGCTarget(compact_summary_bucket_spread_name, summaryService, docStore,
                      std::move(doc_type_and_subdb_name_in)) {
}

size_t SummaryCompactSpreadTarget::getBloat(const search::IDocumentStore& docStore) const {
    return docStore.getMaxSpreadAsBloat();
}

void SummaryCompactSpreadTarget::create(IDocumentStore& docStore, FlushStats& stats, SerialNum currSerial,
                                        TaskPromise task_promise) {
    // called by document db summary executor
    auto task = std::make_unique<CompactSpread>(docStore, stats, currSerial, *_doc_type_and_subdb_name);
    task_promise.set_value(std::move(task));
}

} // namespace proton
