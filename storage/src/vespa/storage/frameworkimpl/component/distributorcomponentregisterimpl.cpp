// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/storage/frameworkimpl/component/distributorcomponentregisterimpl.h>

#include <vespa/log/log.h>
#include <vespa/vdslib/distribution/idealnodecalculatorimpl.h>

LOG_SETUP(".storage.component.register.distributor");

namespace storage {

DistributorComponentRegisterImpl::DistributorComponentRegisterImpl()
    : _timeCalculator(0),
      _bucketDatabase(),
      _idealNodeCalculator(new lib::IdealNodeCalculatorImpl)
{
    _idealNodeCalculator->setClusterState(_clusterState);
}

void
DistributorComponentRegisterImpl::handleNewState()
{
    _clusterState = *getNodeStateUpdater().getSystemState();
}

void
DistributorComponentRegisterImpl::registerDistributorComponent(
        DistributorManagedComponent& smc)
{
    vespalib::LockGuard lock(_componentLock);
    _components.push_back(&smc);
    if (_timeCalculator != 0) {
        smc.setTimeCalculator(*_timeCalculator);
    }
    smc.setBucketDatabase(_bucketDatabase);
    smc.setDistributorConfig(_distributorConfig);
    smc.setVisitorConfig(_visitorConfig);
    smc.setIdealNodeCalculator(*_idealNodeCalculator);
}

void
DistributorComponentRegisterImpl::setIdealNodeCalculator(
        std::unique_ptr<lib::IdealNodeCalculatorConfigurable> calc)
{
    _idealNodeCalculator = std::move(calc);
    for (uint32_t i=0; i<_components.size(); ++i) {
        _components[i]->setIdealNodeCalculator(*_idealNodeCalculator);
    }
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
DistributorComponentRegisterImpl::setDistributorConfig(
        const DistributorConfig& c)
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
DistributorComponentRegisterImpl::setDistribution(lib::Distribution::SP d)
{
    StorageComponentRegisterImpl::setDistribution(d);
    _idealNodeCalculator->setDistribution(*d);
}

void
DistributorComponentRegisterImpl::setNodeStateUpdater(NodeStateUpdater& updater)
{
    StorageComponentRegisterImpl::setNodeStateUpdater(updater);
    if (updater.getSystemState().get() != 0) {
        _clusterState = *updater.getSystemState();
    }
    updater.addStateListener(*this);
}

} // storage
