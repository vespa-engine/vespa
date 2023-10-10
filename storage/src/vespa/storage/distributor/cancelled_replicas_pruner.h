// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/bucketdb/bucketcopy.h>
#include <vespa/storage/distributor/operations/cancel_scope.h>
#include <span>
#include <vector>

namespace storage::distributor {

/**
 * Returns a new vector that contains all entries of `replicas` whose nodes are _not_ tagged as
 * cancelled in `cancel_scope`. Returned entry ordering is identical to input ordering.
 */
[[nodiscard]] std::vector<BucketCopy> prune_cancelled_nodes(std::span<const BucketCopy> replicas, const CancelScope& cancel_scope);

}
