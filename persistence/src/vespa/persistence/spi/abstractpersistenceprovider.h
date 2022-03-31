// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "persistenceprovider.h"

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
    Result initialize() override { return Result(); };
    Result removeEntry(const Bucket&, Timestamp) override { return Result(); }
    void removeIfFoundAsync(const Bucket&, Timestamp, const DocumentId&, OperationComplete::UP) override;
    Result setClusterState(BucketSpace, const ClusterState&) override { return Result(); }
    BucketIdListResult getModifiedBuckets(BucketSpace bucketSpace) const override;
};

}
