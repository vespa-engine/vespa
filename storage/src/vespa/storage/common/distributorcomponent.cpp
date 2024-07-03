// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributorcomponent.h"
#include <vespa/storage/config/distributorconfiguration.h>
#include <vespa/storage/config/config-stor-distributormanager.h>
#include <vespa/storage/config/config-stor-visitordispatcher.h>

namespace storage {

DistributorComponent::DistributorComponent(DistributorComponentRegister& compReg,
                                           std::string_view name)
    : StorageComponent(compReg, name),
      _timeCalculator(nullptr),
      _distributorConfig(std::make_unique<DistributorManagerConfig>()),
      _visitorConfig(std::make_unique<VisitorDispatcherConfig>()),
      _internal_config_generation(0),
      _config_snapshot(std::make_shared<DistributorConfiguration>(*this))
{
    compReg.registerDistributorComponent(*this);
}

DistributorComponent::~DistributorComponent() = default;

void
DistributorComponent::update_config_snapshot() {
    auto new_snapshot = std::make_shared<DistributorConfiguration>(*this);
    new_snapshot->configure(*_visitorConfig);
    new_snapshot->configure(*_distributorConfig);
    // TODO make thread safe if necessary; access currently synchronized by config updates
    // and checks all being routed through the same "critical tick" global lock.
    ++_internal_config_generation;
    _config_snapshot = std::move(new_snapshot);
}

void
DistributorComponent::setDistributorConfig(const DistributorManagerConfig& cfg) {
    _distributorConfig = std::make_unique<DistributorManagerConfig>(cfg);
    update_config_snapshot();
}

void
DistributorComponent::setVisitorConfig(const VisitorDispatcherConfig& cfg)  {
    _visitorConfig = std::make_unique<VisitorDispatcherConfig>(cfg);
    update_config_snapshot();
}

} // storage

