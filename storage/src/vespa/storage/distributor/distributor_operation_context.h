// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "storage_node_up_states.h"
#include <vespa/storageapi/defs.h>

namespace storage { class DistributorConfiguration; }

namespace storage::distributor {

class BucketSpaceStateMap;
class DistributorBucketSpaceRepo;

/**
 * Interface with functionality that is used when handling top-level distributor operations.
 */
class DistributorOperationContext {
public:
    virtual ~DistributorOperationContext() = default;
    virtual api::Timestamp generate_unique_timestamp() = 0;
    virtual const BucketSpaceStateMap& bucket_space_states() const noexcept = 0;
    virtual BucketSpaceStateMap& bucket_space_states() noexcept = 0;
    virtual const DistributorConfiguration& distributor_config() const noexcept = 0;
};

}
