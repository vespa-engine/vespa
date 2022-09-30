// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributorcomponent.h"

namespace storage {

DistributorComponent::DistributorComponent(DistributorComponentRegister& compReg,
                                           vespalib::stringref name)
    : StorageComponent(compReg, name),
      _timeCalculator(0),
      _distributorConfig(),
      _visitorConfig(),
      _internal_config_generation(0),
      _config_snapshot(std::make_shared<DistributorConfiguration>(*this))
{
    compReg.registerDistributorComponent(*this);
}

DistributorComponent::~DistributorComponent() = default;

void DistributorComponent::update_config_snapshot() {
    auto new_snapshot = std::make_shared<DistributorConfiguration>(*this);
    new_snapshot->configure(_visitorConfig);
    new_snapshot->configure(_distributorConfig);
    // TODO make thread safe if necessary; access currently synchronized by config updates
    // and checks all being routed through the same "critical tick" global lock.
    ++_internal_config_generation;
    _config_snapshot = std::move(new_snapshot);
}

} // storage

