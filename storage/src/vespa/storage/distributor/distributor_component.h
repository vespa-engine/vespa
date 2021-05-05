// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

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
    // TODO STRIPE: When legacy mode is removed, replace this with mapping from BucketSpace to struct with
    //              lib::ClusterState and lib::Distribution (need by BucketDBUpdater).
    std::unique_ptr<DistributorBucketSpaceRepo> _bucket_space_repo;
    std::unique_ptr<DistributorBucketSpaceRepo> _read_only_bucket_space_repo;

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
    const DistributorBucketSpaceRepo& bucket_space_repo() const noexcept override {
        return *_bucket_space_repo;
    }
    DistributorBucketSpaceRepo& bucket_space_repo() noexcept override {
        return *_bucket_space_repo;
    }
    const DistributorBucketSpaceRepo& read_only_bucket_space_repo() const noexcept override {
        return *_read_only_bucket_space_repo;
    }
    DistributorBucketSpaceRepo& read_only_bucket_space_repo() noexcept override {
        return *_read_only_bucket_space_repo;
    }
    const storage::DistributorConfiguration& distributor_config() const noexcept override {
        return _distributor.config();
    }


};

}
