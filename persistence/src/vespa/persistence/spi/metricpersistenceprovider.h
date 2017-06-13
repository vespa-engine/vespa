// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * SPI implementation wrapper to add metrics.
 */

#pragma once

#include "persistenceprovider.h"
#include <vespa/metrics/metrics.h>

namespace storage {
namespace spi {

class MetricPersistenceProvider : public PersistenceProvider,
                                  public metrics::MetricSet
{
    struct ResultMetrics : public metrics::MetricSet {
        std::vector<std::unique_ptr<metrics::DoubleAverageMetric> > _metric;

        ResultMetrics(const char* opName);
        ~ResultMetrics();
    };
    PersistenceProvider* _next;
    std::vector<std::unique_ptr<ResultMetrics>> _functionMetrics;

public:
    typedef std::unique_ptr<MetricPersistenceProvider> UP;

    MetricPersistenceProvider(PersistenceProvider&);
    ~MetricPersistenceProvider();

    void setNextProvider(PersistenceProvider& p) { _next = &p; }

    // Implementation of the PersistenceProvider API
    Result initialize() override;
    PartitionStateListResult getPartitionStates() const override;
    BucketIdListResult listBuckets(PartitionId) const override;
    Result setClusterState(const ClusterState&) override;
    Result setActiveState(const Bucket&, BucketInfo::ActiveState) override;
    BucketInfoResult getBucketInfo(const Bucket&) const override;
    Result put(const Bucket&, Timestamp, const DocumentSP&, Context&) override;
    RemoveResult remove(const Bucket&, Timestamp, const DocumentId&, Context&) override;
    RemoveResult removeIfFound(const Bucket&, Timestamp, const DocumentId&, Context&) override;
    Result removeEntry(const Bucket&, Timestamp, Context&) override;
    UpdateResult update(const Bucket&, Timestamp, const DocumentUpdateSP&, Context&) override;
    Result flush(const Bucket&, Context&) override;
    GetResult get(const Bucket&, const document::FieldSet&, const DocumentId&, Context&) const override;
    CreateIteratorResult createIterator(const Bucket&, const document::FieldSet&, const Selection&,
                                        IncludedVersions, Context&) override;
    IterateResult iterate(IteratorId, uint64_t maxByteSize, Context&) const override;
    Result destroyIterator(IteratorId, Context&) override;
    Result createBucket(const Bucket&, Context&) override;
    Result deleteBucket(const Bucket&, Context&) override;
    BucketIdListResult getModifiedBuckets() const override;
    Result maintain(const Bucket&, MaintenanceLevel level) override;
    Result split(const Bucket& source, const Bucket& target1, const Bucket& target2, Context&) override;
    Result join(const Bucket& source1, const Bucket& source2, const Bucket& target, Context&) override;
    Result move(const Bucket&, PartitionId target, Context&) override;

private:
    void defineResultMetrics(int index, const char* name);
};

} // spi
} // storage
