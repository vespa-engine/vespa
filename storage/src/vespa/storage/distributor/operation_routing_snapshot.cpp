// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "operation_routing_snapshot.h"

namespace storage::distributor {

OperationRoutingSnapshot::OperationRoutingSnapshot(std::shared_ptr<ClusterDistributionContext> context,
                                                   std::shared_ptr<BucketDatabase::ReadGuard> read_guard)
    : _context(std::move(context)),
      _read_guard(std::move(read_guard))
{}

OperationRoutingSnapshot::~OperationRoutingSnapshot() = default;

OperationRoutingSnapshot OperationRoutingSnapshot::make_not_routable_in_state(
        std::shared_ptr<ClusterDistributionContext> context)
{
    return OperationRoutingSnapshot(std::move(context), std::shared_ptr<BucketDatabase::ReadGuard>());
}

OperationRoutingSnapshot OperationRoutingSnapshot::make_routable_with_guard(
        std::shared_ptr<ClusterDistributionContext> context,
        std::shared_ptr<BucketDatabase::ReadGuard> read_guard)
{
    return OperationRoutingSnapshot(std::move(context), std::move(read_guard));
}

}
