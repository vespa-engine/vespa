// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucket_space_state_map.h"
#include "distributor_interface.h"
#include "distributor_node_context.h"
#include "distributor_operation_context.h"
#include <vespa/storage/common/distributorcomponent.h>

namespace storage::distributor {

class DistributorBucketSpaceRepo;

/**
 * The framework component for the top-level distributor.
 *
 * This class should be used directly as little as possible.
 * Instead the interfaces DistributorNodeContext and DistributorOperationContext should be used where possible.
 */
class DistributorComponent : public storage::DistributorComponent,
                             public DistributorNodeContext,
                             public DistributorOperationContext {
private:
    DistributorInterface& _distributor;
    BucketSpaceStateMap   _bucket_space_states;


public:
    DistributorComponent(DistributorInterface& distributor,
                         DistributorComponentRegister& comp_reg,
                         const std::string& name);

    ~DistributorComponent() override;

    // TODO STRIPE: Unify implementation of this interface between DistributorComponent and DistributorStripeComponent?
    // Implements DistributorNodeContext
    const framework::Clock& clock() const noexcept override { return getClock(); }
    const vespalib::string* cluster_name_ptr() const noexcept override { return cluster_context().cluster_name_ptr(); }
    const document::BucketIdFactory& bucket_id_factory() const noexcept override { return getBucketIdFactory(); }
    uint16_t node_index() const noexcept override { return getIndex(); }
    api::StorageMessageAddress node_address(uint16_t node_index) const noexcept override;

    // Implements DistributorOperationContext
    api::Timestamp generate_unique_timestamp() override {
        return getUniqueTimestamp();
    }
    const BucketSpaceStateMap& bucket_space_states() const noexcept override {
        return _bucket_space_states;
    }
    BucketSpaceStateMap& bucket_space_states() noexcept override {
        return _bucket_space_states;
    }
    const storage::DistributorConfiguration& distributor_config() const noexcept override {
        return _distributor.config();
    }
};

}
