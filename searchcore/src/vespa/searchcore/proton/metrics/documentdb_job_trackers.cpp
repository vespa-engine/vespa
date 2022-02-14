// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentdb_job_trackers.h"
#include "job_tracked_flush_target.h"

#include <vespa/log/log.h>
LOG_SETUP(".proton.metrics.documentdb_job_trackers");

using searchcorespi::IFlushTarget;
typedef IFlushTarget::Type FTT;
typedef IFlushTarget::Component FTC;

using time_point = std::chrono::time_point<std::chrono::steady_clock>;

namespace proton {

DocumentDBJobTrackers::DocumentDBJobTrackers()
    : _lock(),
      _now(std::chrono::steady_clock::now()),
      _attributeFlush(std::make_shared<JobTracker>(_now, _lock)),
      _memoryIndexFlush(std::make_shared<JobTracker>(_now, _lock)),
      _diskIndexFusion(std::make_shared<JobTracker>(_now, _lock)),
      _documentStoreFlush(std::make_shared<JobTracker>(_now, _lock)),
      _documentStoreCompact(std::make_shared<JobTracker>(_now, _lock)),
      _bucketMove(std::make_shared<JobTracker>(_now, _lock)),
      _lidSpaceCompact(std::make_shared<JobTracker>(_now, _lock)),
      _removedDocumentsPrune(std::make_shared<JobTracker>(_now, _lock))
{
}

DocumentDBJobTrackers::~DocumentDBJobTrackers() = default;

namespace {

IFlushTarget::SP
trackFlushTarget(std::shared_ptr<IJobTracker> tracker,
                 std::shared_ptr<IFlushTarget> target)
{
    return std::make_shared<JobTrackedFlushTarget>(std::move(tracker), std::move(target));
}

}

IFlushTarget::List
DocumentDBJobTrackers::trackFlushTargets(const IFlushTarget::List &flushTargets)
{
    IFlushTarget::List retval;
    for (const auto &ft : flushTargets) {
        if (ft->getComponent() == FTC::ATTRIBUTE && ft->getType() == FTT::SYNC) {
            retval.push_back(trackFlushTarget(_attributeFlush, ft));
        } else if (ft->getComponent() == FTC::ATTRIBUTE && ft->getType() == FTT::GC) {
            retval.push_back(trackFlushTarget(_attributeFlush, ft));
        } else if (ft->getComponent() == FTC::INDEX && ft->getType() == FTT::FLUSH) {
            retval.push_back(trackFlushTarget(_memoryIndexFlush, ft));
        } else if (ft->getComponent() == FTC::INDEX && ft->getType() == FTT::GC) {
            retval.push_back(trackFlushTarget(_diskIndexFusion, ft));
        } else if (ft->getComponent() == FTC::DOCUMENT_STORE && ft->getType() == FTT::SYNC) {
            retval.push_back(trackFlushTarget(_documentStoreFlush, ft));
        } else if (ft->getComponent() == FTC::DOCUMENT_STORE && ft->getType() == FTT::GC) {
            retval.push_back(trackFlushTarget(_documentStoreCompact, ft));
        } else {
            LOG(warning, "trackFlushTargets(): Flush target '%s' with type '%d' and component '%d' "
                    "is not known and will not be tracked",
                    ft->getName().c_str(), static_cast<int>(ft->getType()),
                    static_cast<int>(ft->getComponent()));
            retval.push_back(ft);
        }
    }
    return retval;
}

namespace {

double
updateMetric(metrics::DoubleAverageMetric &metric,
             JobTracker &tracker,
             time_point now,
             const std::lock_guard<std::mutex> &guard)
{
    double load = tracker.sampleLoad(now, guard);
    metric.addValue(load);
    return load;
}

}

void
DocumentDBJobTrackers::updateMetrics(DocumentDBTaggedMetrics::JobMetrics &metrics)
{
    std::lock_guard<std::mutex> guard(_lock);
    _now = std::chrono::steady_clock::now();
    double load = 0.0;
    load += updateMetric(metrics.attributeFlush, *_attributeFlush, _now, guard);
    load += updateMetric(metrics.memoryIndexFlush, *_memoryIndexFlush, _now, guard);
    load += updateMetric(metrics.diskIndexFusion, *_diskIndexFusion, _now, guard);
    load += updateMetric(metrics.documentStoreFlush, *_documentStoreFlush, _now, guard);
    load += updateMetric(metrics.documentStoreCompact, *_documentStoreCompact, _now, guard);
    load += updateMetric(metrics.bucketMove, *_bucketMove, _now, guard);
    load += updateMetric(metrics.lidSpaceCompact, *_lidSpaceCompact, _now, guard);
    load += updateMetric(metrics.removedDocumentsPrune, *_removedDocumentsPrune, _now, guard);
    metrics.total.addValue(load);
}

} // namespace proton
