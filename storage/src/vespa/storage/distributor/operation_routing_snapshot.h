// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <memory>

namespace storage::distributor {

class BucketSpaceDistributionContext;
class DistributorBucketSpaceRepo;

/**
 * An "operation routing snapshot" is intended to provide a stable means of computing
 * bucket routing targets and performing database lookups for a particular bucket space
 * in a potentially multi-threaded setting. When using multiple threads, both the current
 * cluster/distribution state as well as the underlying bucket database may change
 * independent of each other when observed from any other thread than the main distributor
 * thread. Additionally, the bucket management system may operate with separate read-only
 * databases during state transitions, complicating things further.
 *
 * By using an OperationRoutingSnapshot, a caller gets a consistent view of the world
 * that stays valid throughout the operation's life time.
 *
 * Note that holding the DB read guard should be done for as short a time as possible to
 * avoid elevated memory usage caused by data stores not being able to free on-hold items.
 */
class OperationRoutingSnapshot {
    std::shared_ptr<const BucketSpaceDistributionContext> _context;
    std::shared_ptr<BucketDatabase::ReadGuard> _read_guard;
    const DistributorBucketSpaceRepo* _bucket_space_repo;
public:
    OperationRoutingSnapshot(std::shared_ptr<const BucketSpaceDistributionContext> context,
                             std::shared_ptr<BucketDatabase::ReadGuard> read_guard,
                             const DistributorBucketSpaceRepo* bucket_space_repo);

    static OperationRoutingSnapshot make_not_routable_in_state(std::shared_ptr<const BucketSpaceDistributionContext> context);
    static OperationRoutingSnapshot make_routable_with_guard(std::shared_ptr<const BucketSpaceDistributionContext> context,
                                                             std::shared_ptr<BucketDatabase::ReadGuard> read_guard,
                                                             const DistributorBucketSpaceRepo& bucket_space_repo);

    OperationRoutingSnapshot(const OperationRoutingSnapshot&) noexcept = default;
    OperationRoutingSnapshot& operator=(const OperationRoutingSnapshot&) noexcept = default;
    OperationRoutingSnapshot(OperationRoutingSnapshot&&) noexcept = default;
    OperationRoutingSnapshot& operator=(OperationRoutingSnapshot&&) noexcept = default;

    ~OperationRoutingSnapshot();

    const BucketSpaceDistributionContext& context() const noexcept { return *_context; }
    std::shared_ptr<BucketDatabase::ReadGuard> steal_read_guard() noexcept {
        return std::move(_read_guard);
    }
    bool is_routable() const noexcept {
        return (_read_guard.get() != nullptr);
    }
    const DistributorBucketSpaceRepo* bucket_space_repo() const noexcept {
        return _bucket_space_repo;
    }
};

}
