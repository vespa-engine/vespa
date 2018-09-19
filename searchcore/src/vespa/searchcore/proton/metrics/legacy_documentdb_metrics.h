// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "executor_metrics.h"
#include "legacy_attribute_metrics.h"
#include "legacy_sessionmanager_metrics.h"
#include <vespa/metrics/summetric.h>
#include <vespa/searchcore/proton/matching/matching_stats.h>

namespace proton {

/**
 * Metric set for all legacy metrics reported for a document db.
 *
 * All these metrics have the document type name as part of the metric name,
 * which is not flexible for setting up default metric graph dashboards.
 *
 * TODO: Remove on Vespa 7
 *
 * @deprecated Use DocumentDBTaggedMetrics for all new metrics.
 */
struct LegacyDocumentDBMetrics : metrics::MetricSet
{
    struct IndexMetrics : metrics::MetricSet {
        metrics::LongValueMetric memoryUsage;
        metrics::LongValueMetric docsInMemory;
        metrics::LongValueMetric diskUsage;

        IndexMetrics(metrics::MetricSet *parent);
        ~IndexMetrics();
    };

    struct DocstoreMetrics : metrics::MetricSet {
        metrics::LongValueMetric memoryUsage;
        metrics::LongCountMetric cacheLookups;
        metrics::LongAverageMetric cacheHitRate;
        metrics::LongValueMetric cacheElements;
        metrics::LongValueMetric cacheMemoryUsed;

        DocstoreMetrics(metrics::MetricSet *parent);
        ~DocstoreMetrics();
    };

    struct MatchingMetrics : metrics::MetricSet {
        metrics::LongCountMetric docsMatched;
        metrics::LongCountMetric docsRanked;
        metrics::LongCountMetric docsReRanked;
        metrics::LongCountMetric queries;
        metrics::DoubleValueMetric softDoomFactor;
        metrics::DoubleAverageMetric queryCollateralTime;
        metrics::DoubleAverageMetric queryLatency;

        struct RankProfileMetrics : metrics::MetricSet {
            struct DocIdPartition : metrics::MetricSet {
                metrics::LongCountMetric docsMatched;
                metrics::LongCountMetric docsRanked;
                metrics::LongCountMetric docsReRanked;
                metrics::DoubleAverageMetric active_time;
                metrics::DoubleAverageMetric wait_time;

                using UP = std::unique_ptr<DocIdPartition>;
                DocIdPartition(const std::string &name, metrics::MetricSet *parent);
                ~DocIdPartition();
                void update(const matching::MatchingStats::Partition &stats);
            };
            using DocIdPartitions = std::vector<DocIdPartition::UP>;
            using UP = std::unique_ptr<RankProfileMetrics>;

            metrics::LongCountMetric     queries;
            metrics::LongCountMetric     limited_queries;        
            metrics::DoubleAverageMetric matchTime;
            metrics::DoubleAverageMetric groupingTime;
            metrics::DoubleAverageMetric rerankTime;
            DocIdPartitions              partitions;

            RankProfileMetrics(const std::string &name,
                               size_t numDocIdPartitions,
                               metrics::MetricSet *parent);
            ~RankProfileMetrics();
            void update(const matching::MatchingStats &stats);

        };
        using  RankProfileMap = std::map<std::string, RankProfileMetrics::UP>;
        RankProfileMap rank_profiles;

        void update(const matching::MatchingStats &stats);
        MatchingMetrics(metrics::MetricSet *parent);
        ~MatchingMetrics();
    };

    struct SubDBMetrics : metrics::MetricSet
    {
        struct DocumentMetaStoreMetrics : metrics::MetricSet
        {
            metrics::LongValueMetric lidLimit;
            metrics::LongValueMetric usedLids;
            metrics::LongValueMetric lowestFreeLid;
            metrics::LongValueMetric highestUsedLid;
            metrics::DoubleValueMetric lidBloatFactor;
            metrics::DoubleValueMetric lidFragmentationFactor;

            DocumentMetaStoreMetrics(metrics::MetricSet *parent);
            ~DocumentMetaStoreMetrics();
        };

        LegacyAttributeMetrics attributes;
        DocumentMetaStoreMetrics docMetaStore;
        SubDBMetrics(const vespalib::string &name, metrics::MetricSet *parent);
        ~SubDBMetrics();
    };

    IndexMetrics                                 index;
    LegacyAttributeMetrics                       attributes;
    DocstoreMetrics                              docstore;
    MatchingMetrics                              matching;
    ExecutorMetrics                              executor;
    ExecutorMetrics                              indexExecutor;
    ExecutorMetrics                              summaryExecutor;
    LegacySessionManagerMetrics                  sessionManager;
    SubDBMetrics                                 ready;
    SubDBMetrics                                 notReady;
    SubDBMetrics                                 removed;
    metrics::SumMetric<metrics::LongValueMetric> memoryUsage;
    metrics::LongValueMetric                     numDocs;
    metrics::LongValueMetric                     numActiveDocs;
    metrics::LongValueMetric                     numIndexedDocs;
    metrics::LongValueMetric                     numStoredDocs;
    metrics::LongValueMetric                     numRemovedDocs;
    metrics::LongValueMetric                     numBadConfigs;
    size_t                                      _maxNumThreads;

    LegacyDocumentDBMetrics(const std::string &docTypeName, size_t maxNumThreads);
    ~LegacyDocumentDBMetrics();
};

} // namespace proton

