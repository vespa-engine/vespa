// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "attribute_metrics.h"
#include "cache_metrics.h"
#include "memory_usage_metrics.h"
#include "executor_threading_service_metrics.h"
#include "document_db_feeding_metrics.h"
#include "index_metrics.h"
#include <vespa/metrics/metricset.h>
#include <vespa/metrics/valuemetric.h>
#include <vespa/searchcore/proton/matching/matching_stats.h>

namespace metrics { class MetricLockGuard; }

namespace proton {

/**
 * Metrics for a document db that are tagged with at least "documenttype":"name".
 * These tags are exposed as dimensions together with the metrics.
 */
struct DocumentDBTaggedMetrics : metrics::MetricSet
{
    struct JobMetrics : metrics::MetricSet
    {
        metrics::DoubleAverageMetric attributeFlush;
        metrics::DoubleAverageMetric memoryIndexFlush;
        metrics::DoubleAverageMetric diskIndexFusion;
        metrics::DoubleAverageMetric documentStoreFlush;
        metrics::DoubleAverageMetric documentStoreCompact;
        metrics::DoubleAverageMetric bucketMove;
        metrics::DoubleAverageMetric lidSpaceCompact;
        metrics::DoubleAverageMetric removedDocumentsPrune;
        metrics::DoubleAverageMetric total;

        JobMetrics(metrics::MetricSet *parent);
        ~JobMetrics() override;
    };

    struct SubDBMetrics : metrics::MetricSet
    {
        struct LidSpaceMetrics : metrics::MetricSet
        {
            metrics::LongValueMetric lidLimit;
            metrics::LongValueMetric usedLids;
            metrics::LongValueMetric lowestFreeLid;
            metrics::LongValueMetric highestUsedLid;
            metrics::DoubleValueMetric lidBloatFactor;
            metrics::DoubleValueMetric lidFragmentationFactor;

            LidSpaceMetrics(metrics::MetricSet *parent);
            ~LidSpaceMetrics() override;
        };

        struct DocumentStoreMetrics : metrics::MetricSet
        {
            metrics::LongValueMetric diskUsage;
            metrics::LongValueMetric diskBloat;
            metrics::DoubleValueMetric maxBucketSpread;
            MemoryUsageMetrics memoryUsage;
            CacheMetrics cache;

            DocumentStoreMetrics(metrics::MetricSet *parent);
            ~DocumentStoreMetrics() override;
        };

        LidSpaceMetrics lidSpace;
        DocumentStoreMetrics documentStore;
        proton::AttributeMetrics attributes;
        proton::IndexMetrics index;

        SubDBMetrics(const std::string &name, metrics::MetricSet *parent);
        ~SubDBMetrics() override;
    };

    struct AttributeMetrics : metrics::MetricSet
    {
        struct ResourceUsageMetrics : metrics::MetricSet
        {
            metrics::DoubleValueMetric address_space;
            metrics::LongValueMetric   feedingBlocked;

            ResourceUsageMetrics(metrics::MetricSet *parent);
            ~ResourceUsageMetrics() override;
        };

        metrics::LongValueMetric diskUsage;
        ResourceUsageMetrics resourceUsage;
        MemoryUsageMetrics totalMemoryUsage;

        AttributeMetrics(metrics::MetricSet *parent);
        ~AttributeMetrics() override;
    };

    struct IndexMetrics : metrics::MetricSet
    {
        metrics::LongValueMetric diskUsage;
        MemoryUsageMetrics memoryUsage;
        metrics::LongValueMetric docsInMemory;
        DiskIoMetrics disk_io;

        IndexMetrics(metrics::MetricSet *parent);
        ~IndexMetrics() override;
    };

    struct MatchingMetrics : metrics::MetricSet {
        metrics::LongCountMetric docsMatched;
        metrics::LongCountMetric docsRanked;
        metrics::LongCountMetric docsReRanked;
        metrics::LongCountMetric queries;
        metrics::LongCountMetric softDoomedQueries;
        metrics::DoubleAverageMetric querySetupTime;
        metrics::DoubleAverageMetric queryLatency;

        struct RankProfileMetrics : metrics::MetricSet {
            struct DocIdPartition : metrics::MetricSet {
                metrics::LongCountMetric docsMatched;
                metrics::LongCountMetric docsRanked;
                metrics::LongCountMetric docsReRanked;
                metrics::DoubleAverageMetric activeTime;
                metrics::DoubleAverageMetric waitTime;

                using UP = std::unique_ptr<DocIdPartition>;
                DocIdPartition(const std::string &name, metrics::MetricSet *parent);
                ~DocIdPartition() override;
                void update(const matching::MatchingStats::Partition &stats);
            };
            using DocIdPartitions = std::vector<DocIdPartition::UP>;
            using UP = std::unique_ptr<RankProfileMetrics>;

            metrics::LongCountMetric     docsMatched;
            metrics::LongCountMetric     docsRanked;
            metrics::LongCountMetric     docsReRanked;
            metrics::LongCountMetric     queries;
            metrics::LongCountMetric     limitedQueries;
            metrics::LongCountMetric     softDoomedQueries;
            metrics::DoubleValueMetric   softDoomFactor;
            metrics::DoubleAverageMetric matchTime;
            metrics::DoubleAverageMetric groupingTime;
            metrics::DoubleAverageMetric rerankTime;
            metrics::DoubleAverageMetric querySetupTime;
            metrics::DoubleAverageMetric queryLatency;
            DocIdPartitions              partitions;

            RankProfileMetrics(const std::string &name,
                               size_t numDocIdPartitions,
                               metrics::MetricSet *parent);
            ~RankProfileMetrics() override;
            void update(const metrics::MetricLockGuard & guard, const matching::MatchingStats &stats);

        };
        using  RankProfileMap = std::map<std::string, RankProfileMetrics::UP>;
        RankProfileMap rank_profiles;

        void update(const matching::MatchingStats &stats);
        MatchingMetrics(metrics::MetricSet *parent);
        ~MatchingMetrics() override;
    };

    struct DocumentsMetrics : metrics::MetricSet {
        metrics::LongValueMetric active;
        metrics::LongValueMetric ready;
        metrics::LongValueMetric total;
        metrics::LongValueMetric removed;

        DocumentsMetrics(metrics::MetricSet *parent);
        ~DocumentsMetrics() override;
    };

    struct BucketMoveMetrics : metrics::MetricSet {
        metrics::LongValueMetric bucketsPending;

        BucketMoveMetrics(metrics::MetricSet *parent);
        ~BucketMoveMetrics() override;
    };

    JobMetrics job;
    AttributeMetrics attribute;
    IndexMetrics index;
    SubDBMetrics ready;
    SubDBMetrics notReady;
    SubDBMetrics removed;
    ExecutorThreadingServiceMetrics threadingService;
    MatchingMetrics matching;
    DocumentsMetrics documents;
    BucketMoveMetrics bucketMove;
    DocumentDBFeedingMetrics feeding;
    MemoryUsageMetrics totalMemoryUsage;
    metrics::LongValueMetric totalDiskUsage;
    metrics::DoubleValueMetric heart_beat_age;
    size_t maxNumThreads;

    DocumentDBTaggedMetrics(const std::string &docTypeName, size_t maxNumThreads_);
    ~DocumentDBTaggedMetrics() override;
};

}

