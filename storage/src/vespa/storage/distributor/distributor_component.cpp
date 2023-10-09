// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributor_bucket_space.h"
#include "distributor_bucket_space_repo.h"
#include "distributor_component.h"

namespace storage::distributor {

DistributorComponent::DistributorComponent(DistributorInterface& distributor,
                                           DistributorComponentRegister& comp_reg,
                                           const std::string& name)
    : storage::DistributorComponent(comp_reg, name),
      _distributor(distributor),
      _bucket_space_states()
{
}

DistributorComponent::~DistributorComponent() = default;

api::StorageMessageAddress
DistributorComponent::node_address(uint16_t node_index) const noexcept
{
    return api::StorageMessageAddress::create(cluster_name_ptr(), lib::NodeType::STORAGE, node_index);
}

}
