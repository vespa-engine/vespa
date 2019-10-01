// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <memory>

namespace storage::distributor {

class ClusterDistributionContext;

/**
 * TODO desc
 */
class OperationRoutingSnapshot {
    std::shared_ptr<ClusterDistributionContext> _context;
    std::shared_ptr<BucketDatabase::ReadGuard> _read_guard;
public:
    OperationRoutingSnapshot(std::shared_ptr<ClusterDistributionContext> context,
                             std::shared_ptr<BucketDatabase::ReadGuard> read_guard);

    static OperationRoutingSnapshot make_not_routable_in_state(std::shared_ptr<ClusterDistributionContext> context);
    static OperationRoutingSnapshot make_routable_with_guard(std::shared_ptr<ClusterDistributionContext> context,
                                                             std::shared_ptr<BucketDatabase::ReadGuard> read_guard);

    OperationRoutingSnapshot(const OperationRoutingSnapshot&) noexcept = default;
    OperationRoutingSnapshot& operator=(const OperationRoutingSnapshot&) noexcept = default;
    OperationRoutingSnapshot(OperationRoutingSnapshot&&) noexcept = default;
    OperationRoutingSnapshot& operator=(OperationRoutingSnapshot&&) noexcept = default;

    ~OperationRoutingSnapshot();

    const ClusterDistributionContext& context() const noexcept { return *_context; }
    std::shared_ptr<BucketDatabase::ReadGuard> steal_read_guard() noexcept {
        return std::move(_read_guard);
    }
    bool is_routable() const noexcept {
        return (_read_guard.get() != nullptr);
    }
};

}
