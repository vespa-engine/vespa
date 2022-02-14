// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentdb_tagged_metrics.h"
#include "job_tracker.h"
#include <vespa/searchcorespi/flush/iflushtarget.h>

namespace proton {

/**
 * Class that handles all job trackers for a document db and
 * connects them to the job metrics.
 */
class DocumentDBJobTrackers
{
private:
    using time_point = std::chrono::time_point<std::chrono::steady_clock>;
    using JobTrackerSP = std::shared_ptr<JobTracker>;
    std::mutex      _lock;
    time_point      _now;
    JobTrackerSP    _attributeFlush;
    JobTrackerSP    _memoryIndexFlush;
    JobTrackerSP    _diskIndexFusion;
    JobTrackerSP    _documentStoreFlush;
    JobTrackerSP    _documentStoreCompact;
    JobTrackerSP    _bucketMove;
    JobTrackerSP    _lidSpaceCompact;
    JobTrackerSP    _removedDocumentsPrune;

public:
    DocumentDBJobTrackers();
    DocumentDBJobTrackers(const DocumentDBJobTrackers &) = delete;
    DocumentDBJobTrackers & operator = (const DocumentDBJobTrackers &) = delete;
    ~DocumentDBJobTrackers();

    IJobTracker &getAttributeFlush() { return *_attributeFlush; }
    IJobTracker &getMemoryIndexFlush() { return *_memoryIndexFlush; }
    IJobTracker &getDiskIndexFusion() { return *_diskIndexFusion; }
    IJobTracker &getDocumentStoreFlush() { return *_documentStoreFlush; }
    IJobTracker &getDocumentStoreCompact() { return *_documentStoreCompact; }
    std::shared_ptr<IJobTracker> getBucketMove() { return _bucketMove; }
    std::shared_ptr<IJobTracker> getLidSpaceCompact() { return _lidSpaceCompact; }
    std::shared_ptr<IJobTracker> getRemovedDocumentsPrune() { return _removedDocumentsPrune; }

    searchcorespi::IFlushTarget::List
    trackFlushTargets(const searchcorespi::IFlushTarget::List &flushTargets);

    void updateMetrics(DocumentDBTaggedMetrics::JobMetrics &metrics);
};

}
