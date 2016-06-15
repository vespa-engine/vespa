// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/persistence/spi/persistenceprovider.h>

namespace storage {

namespace spi {

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
    virtual Result initialize() { return Result(); };

    /**
     * Updates the document by calling get(), updating the document,
     * then calling put() on the result.
     */
    virtual UpdateResult update(const Bucket&,
                                Timestamp,
                                const DocumentUpdate::SP&,
                                Context&);

    /**
     * Default impl empty.
     */
    virtual Result createBucket(const Bucket&, Context&) { return Result(); }

    /**
     * Default impl is empty.
     */
    virtual Result maintain(const Bucket&,
                            MaintenanceLevel) { return Result(); }

    /**
     * Default impl is empty.
     */
    virtual Result removeEntry(const Bucket&,
                               Timestamp, Context&) { return Result(); }

    /**
     * Default impl is getBucketInfo();
     */
    virtual Result flush(const Bucket&, Context&) { return Result(); }

    /**
     * Default impl is remove().
     */
    virtual RemoveResult removeIfFound(const Bucket&,
                                       Timestamp,
                                       const DocumentId&,
                                       Context&);

    /**
     * Default impl empty.
     */
    virtual Result setClusterState(const ClusterState&)
    { return Result(); }

    /**
     * Default impl empty.
     */
    virtual Result setActiveState(const Bucket&,
                                  BucketInfo::ActiveState)
    { return Result(); }

    /**
     * Default impl empty.
     */
    virtual BucketIdListResult getModifiedBuckets() const;

    /**
     * Uses join by default.
     */
    virtual Result move(const Bucket& source, PartitionId id, Context&);
};

}

}

