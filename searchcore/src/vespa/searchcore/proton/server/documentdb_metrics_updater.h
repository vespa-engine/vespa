// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "feed_handler_stats.h"
#include <vespa/searchcore/proton/metrics/documentdb_tagged_metrics.h>
#include <vespa/vespalib/stllike/cache_stats.h>
#include <optional>

namespace proton {

class AttributeUsageFilter;
class DDBState;
class DocumentDBJobTrackers;
class DocumentSubDBCollection;
class ExecutorThreadingService;
class ExecutorThreadingServiceStats;
class FeedHandler;

/**
 * Class used to update metrics for a document db.
 */
class DocumentDBMetricsUpdater {
    const DocumentSubDBCollection &_subDBs;
    ExecutorThreadingService      &_writeService;
    DocumentDBJobTrackers         &_jobTrackers;
    const AttributeUsageFilter    &_writeFilter;
    FeedHandler                   &_feed_handler;
    std::optional<FeedHandlerStats> _last_feed_handler_stats;

    void updateMiscMetrics(DocumentDBTaggedMetrics &metrics, const ExecutorThreadingServiceStats &threadingServiceStats);
    void updateAttributeResourceUsageMetrics(DocumentDBTaggedMetrics::AttributeMetrics &metrics);

public:
    DocumentDBMetricsUpdater(const DocumentSubDBCollection &subDBs,
                             ExecutorThreadingService &writeService,
                             DocumentDBJobTrackers &jobTrackers,
                             const AttributeUsageFilter &writeFilter,
                             FeedHandler& feed_handler);
    ~DocumentDBMetricsUpdater();

    void updateMetrics(const metrics::MetricLockGuard & guard, DocumentDBTaggedMetrics &metrics);

};

}
