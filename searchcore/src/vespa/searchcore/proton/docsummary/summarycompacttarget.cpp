// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summarycompacttarget.h"

#include <vespa/vespalib/util/lambdatask.h>

#include <future>

using search::IDocumentStore;
using search::SerialNum;
using searchcorespi::FlushStats;
using searchcorespi::FlushTask;
using searchcorespi::IFlushTarget;
using vespalib::makeLambdaTask;

namespace proton {

namespace {

class Compacter : public FlushTask {
private:
    IDocumentStore& _docStore;
    FlushStats&     _stats;
    SerialNum       _currSerial;
    virtual void compact(IDocumentStore& docStore, SerialNum currSerial) const = 0;

public:
    Compacter(IDocumentStore& docStore, FlushStats& stats, SerialNum currSerial)
        : _docStore(docStore), _stats(stats), _currSerial(currSerial) {}
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
    CompactBloat(IDocumentStore& docStore, FlushStats& stats, SerialNum currSerial)
        : Compacter(docStore, stats, currSerial) {}

private:
    void compact(IDocumentStore& docStore, SerialNum currSerial) const override { docStore.compactBloat(currSerial); }
};

class CompactSpread : public Compacter {
public:
    CompactSpread(IDocumentStore& docStore, FlushStats& stats, SerialNum currSerial)
        : Compacter(docStore, stats, currSerial) {}

private:
    void compact(IDocumentStore& docStore, SerialNum currSerial) const override {
        docStore.compactSpread(currSerial);
    }
};

} // namespace

SummaryGCTarget::SummaryGCTarget(const std::string& name, vespalib::Executor& summaryService,
                                 IDocumentStore& docStore)
    : LeafFlushTarget(name, Type::GC, Component::DOCUMENT_STORE),
      _summaryService(summaryService),
      _docStore(docStore),
      _lastStats() {
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
    // Called by document db executor
    TaskPromise proxied_task_promise;
    auto        future_task = proxied_task_promise.get_future();
    _summaryService.execute(
        makeLambdaTask([this, currentSerial, proxied_task_promise(std::move(proxied_task_promise))]() mutable {
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

SummaryCompactBloatTarget::SummaryCompactBloatTarget(vespalib::Executor& summaryService, IDocumentStore& docStore)
    : SummaryGCTarget("summary.compact_bloat", summaryService, docStore) {
}

size_t SummaryCompactBloatTarget::getBloat(const search::IDocumentStore& docStore) const {
    return docStore.getDiskBloat();
}

void SummaryCompactBloatTarget::create(IDocumentStore& docStore, FlushStats& stats, SerialNum currSerial,
                                       TaskPromise task_promise) {
    // called by summary executor
    task_promise.set_value(std::make_unique<CompactBloat>(docStore, stats, currSerial));
}

SummaryCompactSpreadTarget::SummaryCompactSpreadTarget(vespalib::Executor& summaryService, IDocumentStore& docStore)
    : SummaryGCTarget("summary.compact_spread", summaryService, docStore) {
}

size_t SummaryCompactSpreadTarget::getBloat(const search::IDocumentStore& docStore) const {
    return docStore.getMaxSpreadAsBloat();
}

void SummaryCompactSpreadTarget::create(IDocumentStore& docStore, FlushStats& stats, SerialNum currSerial,
                                        TaskPromise task_promise) {
    // called by summary executor
    task_promise.set_value(std::make_unique<CompactSpread>(docStore, stats, currSerial));
}

} // namespace proton
