// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storage/common/cluster_context.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <cstdint>

namespace document { class BucketIdFactory; }

namespace storage::framework { struct Clock; }

namespace storage::distributor {

/**
 * Interface that provides information and state about a distributor node.
 */
class DistributorNodeContext : public ClusterContext {
public:
    virtual ~DistributorNodeContext() = default;
    virtual const framework::Clock& clock() const noexcept = 0;
    virtual const document::BucketIdFactory& bucket_id_factory() const noexcept = 0;
    virtual uint16_t node_index() const noexcept = 0;
    virtual api::StorageMessageAddress node_address(uint16_t node_index) const noexcept = 0;
};

}

