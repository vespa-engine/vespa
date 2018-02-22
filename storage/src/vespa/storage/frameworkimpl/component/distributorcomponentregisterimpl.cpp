// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "distributorcomponentregisterimpl.h"
#include <vespa/vdslib/distribution/idealnodecalculatorimpl.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>

namespace storage {

DistributorComponentRegisterImpl::DistributorComponentRegisterImpl()
    : _timeCalculator(0)
{
}

DistributorComponentRegisterImpl::~DistributorComponentRegisterImpl() {
}

void
DistributorComponentRegisterImpl::handleNewState()
{
    auto clusterStateBundle = getNodeStateUpdater().getClusterStateBundle();
    _clusterState = *clusterStateBundle->getBaselineClusterState();
}

void
DistributorComponentRegisterImpl::registerDistributorComponent(DistributorManagedComponent& smc)
{
    vespalib::LockGuard lock(_componentLock);
    _components.push_back(&smc);
    if (_timeCalculator != 0) {
        smc.setTimeCalculator(*_timeCalculator);
    }
    smc.setDistributorConfig(_distributorConfig);
    smc.setVisitorConfig(_visitorConfig);
}

void
DistributorComponentRegisterImpl::setTimeCalculator(UniqueTimeCalculator& utc)
{
    vespalib::LockGuard lock(_componentLock);
    if (_timeCalculator != 0) {
        throw vespalib::IllegalStateException(
                "Time calculator already set. Cannot be updated live",
                VESPA_STRLOC);
    }
    _timeCalculator = &utc;
    for (uint32_t i=0; i<_components.size(); ++i) {
        _components[i]->setTimeCalculator(*_timeCalculator);
    }
}

void
DistributorComponentRegisterImpl::setDistributorConfig(const DistributorConfig& c)
{
    vespalib::LockGuard lock(_componentLock);
    _distributorConfig = c;
    for (uint32_t i=0; i<_components.size(); ++i) {
        _components[i]->setDistributorConfig(c);
    }
}

void
DistributorComponentRegisterImpl::setVisitorConfig(const VisitorConfig& c)
{
    vespalib::LockGuard lock(_componentLock);
    _visitorConfig = c;
    for (uint32_t i=0; i<_components.size(); ++i) {
        _components[i]->setVisitorConfig(c);
    }
}

void
DistributorComponentRegisterImpl::setNodeStateUpdater(NodeStateUpdater& updater)
{
    StorageComponentRegisterImpl::setNodeStateUpdater(updater);
    auto clusterStateBundle = updater.getClusterStateBundle();
    if (clusterStateBundle) {
        _clusterState = *clusterStateBundle->getBaselineClusterState();
    }
    updater.addStateListener(*this);
}

} // storage
