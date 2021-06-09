// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "storage_node_up_states.h"
#include <vespa/storageapi/defs.h>

namespace storage { class DistributorConfiguration; }
namespace storage::lib { class ClusterStateBundle; }

namespace storage::distributor {

class DistributorBucketSpaceRepo;

/**
 * Interface with functionality that is used when handling top-level distributor operations.
 */
class DistributorOperationContext {
public:
    virtual ~DistributorOperationContext() {}
    virtual api::Timestamp generate_unique_timestamp() = 0;
    // TODO STRIPE: Access to bucket space repos is only temporary at this level.
    virtual const DistributorBucketSpaceRepo& bucket_space_repo() const noexcept = 0;
    virtual DistributorBucketSpaceRepo& bucket_space_repo() noexcept = 0;
    virtual const DistributorBucketSpaceRepo& read_only_bucket_space_repo() const noexcept = 0;
    virtual DistributorBucketSpaceRepo& read_only_bucket_space_repo() noexcept = 0;
    virtual const DistributorConfiguration& distributor_config() const noexcept = 0;
};

}
