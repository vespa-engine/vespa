// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "distributorcomponentregisterimpl.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/storage/config/config-stor-distributormanager.h>
#include <vespa/storage/config/config-stor-visitordispatcher.h>

namespace storage {

DistributorComponentRegisterImpl::DistributorComponentRegisterImpl()
    : _timeCalculator(nullptr),
      _distributorConfig(std::make_unique<DistributorManagerConfig>()),
      _visitorConfig(std::make_unique<VisitorDispatcherConfig>()),
      _clusterState(std::make_shared<lib::ClusterState>())
{
}

DistributorComponentRegisterImpl::~DistributorComponentRegisterImpl() = default;

void
DistributorComponentRegisterImpl::handleNewState() noexcept
{
    auto clusterStateBundle = getNodeStateUpdater().getClusterStateBundle();
    _clusterState = std::make_shared<lib::ClusterState>(*clusterStateBundle->getBaselineClusterState());
}

void
DistributorComponentRegisterImpl::registerDistributorComponent(DistributorManagedComponent& smc)
{
    std::lock_guard lock(_componentLock);
    _components.push_back(&smc);
    if (_timeCalculator != nullptr) {
        smc.setTimeCalculator(*_timeCalculator);
    }
    smc.setDistributorConfig(*_distributorConfig);
    smc.setVisitorConfig(*_visitorConfig);
}

void
DistributorComponentRegisterImpl::setTimeCalculator(UniqueTimeCalculator& utc)
{
    std::lock_guard lock(_componentLock);
    if (_timeCalculator != nullptr) {
        throw vespalib::IllegalStateException("Time calculator already set. Cannot be updated live", VESPA_STRLOC);
    }
    _timeCalculator = &utc;
    for (auto & component : _components) {
        component->setTimeCalculator(*_timeCalculator);
    }
}

void
DistributorComponentRegisterImpl::setDistributorConfig(const DistributorManagerConfig& cfg)
{
    std::lock_guard lock(_componentLock);
    _distributorConfig = std::make_unique<DistributorManagerConfig>(cfg);
    for (auto & component : _components) {
        component->setDistributorConfig(cfg);
    }
}

void
DistributorComponentRegisterImpl::setVisitorConfig(const VisitorDispatcherConfig& cfg)
{
    std::lock_guard lock(_componentLock);
    _visitorConfig = std::make_unique<VisitorDispatcherConfig>(cfg);
    for (auto & component : _components) {
        component->setVisitorConfig(cfg);
    }
}

void
DistributorComponentRegisterImpl::setNodeStateUpdater(NodeStateUpdater& updater)
{
    StorageComponentRegisterImpl::setNodeStateUpdater(updater);
    auto clusterStateBundle = updater.getClusterStateBundle();
    if (clusterStateBundle) {
        _clusterState = std::make_shared<lib::ClusterState>(*clusterStateBundle->getBaselineClusterState());
    }
    updater.addStateListener(*this);
}

} // storage
