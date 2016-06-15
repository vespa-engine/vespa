// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * SPI implementation wrapper to add metrics.
 */

#pragma once

#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/metrics/metrics.h>

namespace storage {
namespace spi {

class MetricPersistenceProvider : public PersistenceProvider,
                                  public metrics::MetricSet
{
    struct ResultMetrics : public metrics::MetricSet {
        typedef vespalib::LinkedPtr<ResultMetrics> LP;
        std::vector<vespalib::LinkedPtr<metrics::LongAverageMetric> > _metric;

        ResultMetrics(const char* opName);
    };
    PersistenceProvider* _next;
    std::vector<ResultMetrics::LP> _functionMetrics;

public:
    typedef std::unique_ptr<MetricPersistenceProvider> UP;

    MetricPersistenceProvider(PersistenceProvider&);

    void setNextProvider(PersistenceProvider& p) { _next = &p; }

    // Implementation of the PersistenceProvider API
    virtual Result initialize();
    virtual PartitionStateListResult getPartitionStates() const;
    virtual BucketIdListResult listBuckets(PartitionId) const;
    virtual Result setClusterState(const ClusterState&);
    virtual Result setActiveState(const Bucket&, BucketInfo::ActiveState);
    virtual BucketInfoResult getBucketInfo(const Bucket&) const;
    virtual Result put(const Bucket&, Timestamp, const Document::SP&, Context&);
    virtual RemoveResult remove(const Bucket&, Timestamp,
                                const DocumentId&, Context&);
    virtual RemoveResult removeIfFound(const Bucket&, Timestamp,
                                       const DocumentId&, Context&);
    virtual Result removeEntry(const Bucket&, Timestamp, Context&);
    virtual UpdateResult update(const Bucket&, Timestamp,
                                const DocumentUpdate::SP&, Context&);
    virtual Result flush(const Bucket&, Context&);
    virtual GetResult get(const Bucket&, const document::FieldSet&,
                          const DocumentId&, Context&) const;
    virtual CreateIteratorResult createIterator(
            const Bucket&, const document::FieldSet&, const Selection&,
            IncludedVersions, Context&);
    virtual IterateResult iterate(IteratorId, uint64_t maxByteSize,
                                  Context&) const;
    virtual Result destroyIterator(IteratorId, Context&);
    virtual Result createBucket(const Bucket&, Context&);
    virtual Result deleteBucket(const Bucket&, Context&);
    virtual BucketIdListResult getModifiedBuckets() const;
    virtual Result maintain(const Bucket&,
                            MaintenanceLevel level);
    virtual Result split(const Bucket& source, const Bucket& target1,
                         const Bucket& target2, Context&);
    virtual Result join(const Bucket& source1, const Bucket& source2,
                        const Bucket& target, Context&);
    virtual Result move(const Bucket&, PartitionId target, Context&);

private:
    void defineResultMetrics(int index, const char* name);
};

} // spi
} // storage
