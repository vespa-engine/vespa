// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/persistence/spi/persistenceprovider.h>

namespace storage::spi {

/**
 * Simplified abstract persistence provider class. Implements
 * some of the less used functions. Implementors are still encouraged
 * to review the full PersistenceProvider class to verify that
 * their desired behaviour is implemented.
 */
class AbstractPersistenceProvider : public PersistenceProvider
{
public:
    /**
     * Default impl is empty.
     */
    Result initialize() override { return Result(); };

    /**
     * Updates the document by calling get(), updating the document,
     * then calling put() on the result.
     */
    UpdateResult update(const Bucket&, Timestamp, const DocumentUpdateSP&, Context&) override;

    /**
     * Default impl empty.
     */
    Result createBucket(const Bucket&, Context&) override { return Result(); }

    /**
     * Default impl is empty.
     */
    Result maintain(const Bucket&, MaintenanceLevel) override { return Result(); }

    /**
     * Default impl is empty.
     */
    Result removeEntry(const Bucket&, Timestamp, Context&) override { return Result(); }

    /**
     * Default impl is getBucketInfo();
     */
    Result flush(const Bucket&, Context&) override { return Result(); }

    /**
     * Default impl is remove().
     */
    RemoveResult removeIfFound(const Bucket&, Timestamp, const DocumentId&, Context&) override;

    /**
     * Default impl empty.
     */
    Result setClusterState(BucketSpace, const ClusterState&) override { return Result(); }

    /**
     * Default impl empty.
     */
    Result setActiveState(const Bucket&, BucketInfo::ActiveState) override { return Result(); } 
    /**
     * Default impl empty.
     */
    BucketIdListResult getModifiedBuckets(BucketSpace bucketSpace) const override;

    /**
     * Uses join by default.
     */
    Result move(const Bucket& source, PartitionId id, Context&) override;
};

}
