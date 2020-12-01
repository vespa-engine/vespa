// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::FileStorMetrics
 * @ingroup filestorage
 *
 * @brief Metrics for the file store threads.
 *
 * @version $Id$
 */

#pragma once

#include "merge_handler_metrics.h"
#include <vespa/metrics/metricset.h>
#include <vespa/metrics/summetric.h>

namespace storage {

struct FileStorThreadMetrics : public metrics::MetricSet
{
    typedef std::shared_ptr<FileStorThreadMetrics> SP;

    struct Op : metrics::MetricSet {
        std::string _name;
        metrics::LongCountMetric count;
        metrics::DoubleAverageMetric latency;
        metrics::LongCountMetric failed;

        Op(const std::string& id, const std::string& name, MetricSet* owner = nullptr);
        ~Op() override;

        MetricSet * clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                          MetricSet* owner, bool includeUnused) const override;
    };

    template <typename BaseOp>
    struct OpWithRequestSize : BaseOp {
        metrics::LongAverageMetric request_size;

        OpWithRequestSize(const std::string& id, const std::string& name, MetricSet* owner = nullptr);
        ~OpWithRequestSize() override;

        MetricSet * clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                          MetricSet* owner, bool includeUnused) const override;
    };

    template <typename BaseOp>
    struct OpWithTestAndSetFailed : BaseOp {
        metrics::LongCountMetric test_and_set_failed;

        OpWithTestAndSetFailed(const std::string& id, const std::string& name, MetricSet* owner = nullptr);
        ~OpWithTestAndSetFailed() override;

        MetricSet * clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                          MetricSet* owner, bool includeUnused) const override;
    };

    struct OpWithNotFound : Op {
        metrics::LongCountMetric notFound;

        OpWithNotFound(const std::string& id, const std::string& name, metrics::MetricSet* owner = nullptr);
        ~OpWithNotFound() override;
        MetricSet* clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                         MetricSet* owner, bool includeUnused) const override;
    };

    struct Update : OpWithTestAndSetFailed<OpWithRequestSize<OpWithNotFound>> {
        metrics::LongAverageMetric latencyRead;

        explicit Update(MetricSet* owner = nullptr);
        ~Update() override;

        MetricSet* clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                         MetricSet* owner, bool includeUnused) const override;
    };

    struct Visitor : Op {
        metrics::LongAverageMetric documentsPerIterate;

        explicit Visitor(MetricSet* owner = nullptr);
        ~Visitor() override;

        MetricSet * clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                         MetricSet* owner, bool includeUnused) const override;
    };

    // FIXME this daisy-chaining approach to metric set variants is not the prettiest...
    using PutMetricType    = OpWithTestAndSetFailed<OpWithRequestSize<Op>>;
    using GetMetricType    = OpWithRequestSize<OpWithNotFound>;
    using RemoveMetricType = OpWithTestAndSetFailed<OpWithRequestSize<OpWithNotFound>>;

    metrics::LongCountMetric operations;
    metrics::LongCountMetric failedOperations;
    PutMetricType put;
    GetMetricType get;
    RemoveMetricType remove;
    Op removeLocation;
    Op statBucket;
    Update update;
    OpWithNotFound revert;
    Op createIterator;
    Visitor visit;
    Op multiOp;
    Op createBuckets;
    Op deleteBuckets;
    Op repairs;
    metrics::LongCountMetric repairFixed;
    Op recheckBucketInfo;
    Op splitBuckets;
    Op joinBuckets;
    Op setBucketStates;
    Op movedBuckets;
    Op readBucketList;
    Op readBucketInfo;
    Op internalJoin;
    Op mergeBuckets;
    Op getBucketDiff;
    Op applyBucketDiff;
    metrics::LongCountMetric getBucketDiffReply;
    metrics::LongCountMetric applyBucketDiffReply;
    MergeHandlerMetrics merge_handler_metrics;
    metrics::LongAverageMetric batchingSize;

    FileStorThreadMetrics(const std::string& name, const std::string& desc);
    ~FileStorThreadMetrics() override;
};

class FileStorStripeMetrics : public metrics::MetricSet
{
public:
    using SP = std::shared_ptr<FileStorStripeMetrics>;
    metrics::DoubleAverageMetric averageQueueWaitingTime;
    FileStorStripeMetrics(const std::string& name, const std::string& description);
    ~FileStorStripeMetrics() override;
};

class FileStorDiskMetrics : public metrics::MetricSet
{
public:
    using SP = std::shared_ptr<FileStorDiskMetrics>;

    std::vector<FileStorThreadMetrics::SP> threads;
    std::vector<FileStorStripeMetrics::SP> stripes;
    metrics::SumMetric<MetricSet> sumThreads;
    metrics::SumMetric<MetricSet> sumStripes;
    metrics::DoubleAverageMetric averageQueueWaitingTime;
    metrics::LongAverageMetric queueSize;
    metrics::LongAverageMetric pendingMerges;
    metrics::DoubleAverageMetric waitingForLockHitRate;
    metrics::DoubleAverageMetric lockWaitTime;

    FileStorDiskMetrics(const std::string& name, const std::string& description, MetricSet* owner);
    ~FileStorDiskMetrics() override;

    void initDiskMetrics(uint32_t numStripes, uint32_t threadsPerDisk);
};

struct FileStorMetrics : public metrics::MetricSet
{
    FileStorDiskMetrics::SP disk;
    metrics::SumMetric<MetricSet> sum;
    metrics::LongCountMetric directoryEvents;
    metrics::LongCountMetric partitionEvents;
    metrics::LongCountMetric diskEvents;
    metrics::LongAverageMetric bucket_db_init_latency;

    FileStorMetrics();
    ~FileStorMetrics() override;

    void initDiskMetrics(uint32_t numStripes, uint32_t threadsPerDisk);
};

}

