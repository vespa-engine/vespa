// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cassert>

namespace storage::lib { class ClusterState; }

namespace storage::distributor {

class BucketOwnership
{
    const lib::ClusterState* _checkedState;
    bool _owned;

    BucketOwnership(const lib::ClusterState& checkedState) noexcept
        : _checkedState(&checkedState),
          _owned(false)
    { }

public:
    constexpr BucketOwnership() noexcept : _checkedState(nullptr), _owned(true) {}

    [[nodiscard]] bool isOwned() const noexcept { return _owned; }
    /**
     * Cluster state in which the ownership check failed. Lifetime of returned
     * reference depends on when the active or pending cluster state of the
     * distributor may be altered, so it should be used immediately and not
     * stored away. Since the distributor is single threaded, immediate use
     * should be safe.
     *
     * Precondition: isOwned() == false
     */
    const lib::ClusterState& getNonOwnedState() noexcept {
        assert(!isOwned());
        return *_checkedState;
    }

    static constexpr BucketOwnership createOwned() noexcept {
        return BucketOwnership();
    }

    static BucketOwnership createNotOwnedInState(const lib::ClusterState& s) noexcept {
        return BucketOwnership(s);
    }
};

}
