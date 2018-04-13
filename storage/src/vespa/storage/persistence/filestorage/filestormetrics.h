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

#include <vespa/metrics/metrics.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>

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

    struct OpWithNotFound : Op {
        metrics::LongCountMetric notFound;

        OpWithNotFound(const std::string& id, const std::string& name, metrics::MetricSet* owner = nullptr);
        ~OpWithNotFound() override;
        MetricSet* clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                         MetricSet* owner, bool includeUnused) const override;
    };

    struct Update : OpWithRequestSize<OpWithNotFound> {
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

    metrics::LongCountMetric operations;
    metrics::LongCountMetric failedOperations;
    metrics::LoadMetric<OpWithRequestSize<Op>> put;
    metrics::LoadMetric<OpWithRequestSize<OpWithNotFound>> get;
    metrics::LoadMetric<OpWithRequestSize<OpWithNotFound>> remove;
    metrics::LoadMetric<Op> removeLocation;
    metrics::LoadMetric<Op> statBucket;
    metrics::LoadMetric<Update> update;
    metrics::LoadMetric<OpWithNotFound> revert;
    Op createIterator;
    metrics::LoadMetric<Visitor> visit;
    metrics::LoadMetric<Op> multiOp;
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

    metrics::LongCountMetric bytesMerged;
    metrics::LongCountMetric getBucketDiffReply;
    metrics::LongCountMetric applyBucketDiffReply;
    metrics::DoubleAverageMetric mergeLatencyTotal;
    metrics::DoubleAverageMetric mergeMetadataReadLatency;
    metrics::DoubleAverageMetric mergeDataReadLatency;
    metrics::DoubleAverageMetric mergeDataWriteLatency;
    metrics::DoubleAverageMetric mergeAverageDataReceivedNeeded;
    metrics::LongAverageMetric batchingSize;

    FileStorThreadMetrics(const std::string& name, const std::string& desc, const metrics::LoadTypeSet& lt);
    ~FileStorThreadMetrics() override;
};

class FileStorStripeMetrics : public metrics::MetricSet
{
public:
    using SP = std::shared_ptr<FileStorStripeMetrics>;
    metrics::LoadMetric<metrics::DoubleAverageMetric> averageQueueWaitingTime;
    FileStorStripeMetrics(const std::string& name, const std::string& description,
                          const metrics::LoadTypeSet& loadTypes);
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
    metrics::LoadMetric<metrics::DoubleAverageMetric> averageQueueWaitingTime;
    metrics::LongAverageMetric queueSize;
    metrics::LongAverageMetric pendingMerges;
    metrics::DoubleAverageMetric waitingForLockHitRate;
    metrics::DoubleAverageMetric lockWaitTime;

    FileStorDiskMetrics(const std::string& name, const std::string& description,
                        const metrics::LoadTypeSet& loadTypes, MetricSet* owner);
    ~FileStorDiskMetrics() override;

    void initDiskMetrics(const metrics::LoadTypeSet& loadTypes, uint32_t numStripes, uint32_t threadsPerDisk);
};

struct FileStorMetrics : public metrics::MetricSet
{
    std::vector<FileStorDiskMetrics::SP> disks;
    metrics::SumMetric<MetricSet> sum;
    metrics::LongCountMetric directoryEvents;
    metrics::LongCountMetric partitionEvents;
    metrics::LongCountMetric diskEvents;

    explicit FileStorMetrics(const metrics::LoadTypeSet&);
    ~FileStorMetrics() override;

    void initDiskMetrics(uint16_t numDisks, const metrics::LoadTypeSet& loadTypes, uint32_t numStripes, uint32_t threadsPerDisk);
};

}

