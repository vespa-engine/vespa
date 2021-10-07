// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentdb_tagged_metrics.h"
#include "job_tracker.h"
#include <vespa/searchcorespi/flush/iflushtarget.h>
#include <chrono>
#include <mutex>

namespace proton {

/**
 * Class that handles all job trackers for a document db and
 * connects them to the job metrics.
 */
class DocumentDBJobTrackers
{
private:
    std::mutex        _lock;
    using time_point = std::chrono::time_point<std::chrono::steady_clock>;
    time_point        _now;
    JobTracker::SP    _attributeFlush;
    JobTracker::SP    _memoryIndexFlush;
    JobTracker::SP    _diskIndexFusion;
    JobTracker::SP    _documentStoreFlush;
    JobTracker::SP    _documentStoreCompact;
    JobTracker::SP    _bucketMove;
    JobTracker::SP    _lidSpaceCompact;
    JobTracker::SP    _removedDocumentsPrune;

public:
    DocumentDBJobTrackers();
    ~DocumentDBJobTrackers();

    IJobTracker &getAttributeFlush() { return *_attributeFlush; }
    IJobTracker &getMemoryIndexFlush() { return *_memoryIndexFlush; }
    IJobTracker &getDiskIndexFusion() { return *_diskIndexFusion; }
    IJobTracker &getDocumentStoreFlush() { return *_documentStoreFlush; }
    IJobTracker &getDocumentStoreCompact() { return *_documentStoreCompact; }
    IJobTracker::SP getBucketMove() { return _bucketMove; }
    IJobTracker::SP getLidSpaceCompact() { return _lidSpaceCompact; }
    IJobTracker::SP getRemovedDocumentsPrune() { return _removedDocumentsPrune; }

    searchcorespi::IFlushTarget::List
    trackFlushTargets(const searchcorespi::IFlushTarget::List &flushTargets);

    void updateMetrics(DocumentDBTaggedMetrics::JobMetrics &metrics);
};

} // namespace proton

