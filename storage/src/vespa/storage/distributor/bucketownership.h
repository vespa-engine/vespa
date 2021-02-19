// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cassert>

namespace storage::lib { class ClusterState; }

namespace storage::distributor {

class BucketOwnership
{
    const lib::ClusterState* _checkedState;
    bool _owned;

    BucketOwnership(const lib::ClusterState& checkedState)
        : _checkedState(&checkedState),
          _owned(false)
    { }

public:
    BucketOwnership() : _checkedState(nullptr), _owned(true) {}

    bool isOwned() const { return _owned; }
    /**
     * Cluster state in which the ownership check failed. Lifetime of returned
     * reference depends on when the active or pending cluster state of the
     * distributor may be altered, so it should be used immediately and not
     * stored away. Since the distributor is single threaded, immediate use
     * should be safe.
     *
     * Precondition: isOwned() == false
     */
    const lib::ClusterState& getNonOwnedState() {
        assert(!isOwned());
        return *_checkedState;
    }

    static BucketOwnership createOwned() {
        return BucketOwnership();
    }

    static BucketOwnership createNotOwnedInState(const lib::ClusterState& s) {
        return BucketOwnership(s);
    }
};

}
