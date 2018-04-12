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

    struct Op : public metrics::MetricSet {
        std::string _name;
        metrics::LongCountMetric count;
        metrics::DoubleAverageMetric latency;
        metrics::LongCountMetric failed;

        Op(const std::string& id, const std::string name, MetricSet* owner = 0);
        ~Op();

        MetricSet * clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                          MetricSet* owner, bool includeUnused) const override;
        Op* operator&() { return this; }
    };
    struct OpWithNotFound : public Op {
        metrics::LongCountMetric notFound;

        OpWithNotFound(const std::string& id, const std::string name, metrics::MetricSet* owner = 0);
        ~OpWithNotFound();
        MetricSet* clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                         MetricSet* owner, bool includeUnused) const override;
        OpWithNotFound* operator&() { return this; }
    };

    struct Update : public OpWithNotFound {
        metrics::LongAverageMetric latencyRead;

        Update(MetricSet* owner = 0);
        ~Update();

        MetricSet* clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                         MetricSet* owner, bool includeUnused) const override;
        Update* operator&() { return this; }
    };

    struct Visitor : public Op {
        metrics::LongAverageMetric documentsPerIterate;

        Visitor(MetricSet* owner = 0);
        ~Visitor();

        MetricSet * clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                         MetricSet* owner, bool includeUnused) const override;
        Visitor* operator&() { return this; }
    };

    metrics::LongCountMetric operations;
    metrics::LongCountMetric failedOperations;
    metrics::LoadMetric<Op> put;
    metrics::LoadMetric<OpWithNotFound> get;
    metrics::LoadMetric<OpWithNotFound> remove;
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
    ~FileStorThreadMetrics();
};

class FileStorStripeMetrics : public metrics::MetricSet
{
public:
    using SP = std::shared_ptr<FileStorStripeMetrics>;
    metrics::LoadMetric<metrics::DoubleAverageMetric> averageQueueWaitingTime;
    FileStorStripeMetrics(const std::string& name, const std::string& description,
                          const metrics::LoadTypeSet& loadTypes);
    ~FileStorStripeMetrics();
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
    ~FileStorDiskMetrics();

    void initDiskMetrics(const metrics::LoadTypeSet& loadTypes, uint32_t numStripes, uint32_t threadsPerDisk);
};

struct FileStorMetrics : public metrics::MetricSet
{
    std::vector<FileStorDiskMetrics::SP> disks;
    metrics::SumMetric<MetricSet> sum;
    metrics::LongCountMetric directoryEvents;
    metrics::LongCountMetric partitionEvents;
    metrics::LongCountMetric diskEvents;

    FileStorMetrics(const metrics::LoadTypeSet&);
    ~FileStorMetrics();

    void initDiskMetrics(uint16_t numDisks, const metrics::LoadTypeSet& loadTypes, uint32_t numStripes, uint32_t threadsPerDisk);
};

}

