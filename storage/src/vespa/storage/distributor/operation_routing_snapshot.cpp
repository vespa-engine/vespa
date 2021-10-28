// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "operation_routing_snapshot.h"

namespace storage::distributor {

OperationRoutingSnapshot::OperationRoutingSnapshot(std::shared_ptr<const BucketSpaceDistributionContext> context,
                                                   std::shared_ptr<BucketDatabase::ReadGuard> read_guard,
                                                   const DistributorBucketSpaceRepo* bucket_space_repo)
    : _context(std::move(context)),
      _read_guard(std::move(read_guard)),
      _bucket_space_repo(bucket_space_repo)
{}

OperationRoutingSnapshot::~OperationRoutingSnapshot() = default;

OperationRoutingSnapshot OperationRoutingSnapshot::make_not_routable_in_state(
        std::shared_ptr<const BucketSpaceDistributionContext> context)
{
    return OperationRoutingSnapshot(std::move(context), std::shared_ptr<BucketDatabase::ReadGuard>(), nullptr);
}

OperationRoutingSnapshot OperationRoutingSnapshot::make_routable_with_guard(
        std::shared_ptr<const BucketSpaceDistributionContext> context,
        std::shared_ptr<BucketDatabase::ReadGuard> read_guard,
        const DistributorBucketSpaceRepo& bucket_space_repo)
{
    return OperationRoutingSnapshot(std::move(context), std::move(read_guard), &bucket_space_repo);
}

}
