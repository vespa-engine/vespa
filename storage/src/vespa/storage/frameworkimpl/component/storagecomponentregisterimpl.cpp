// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storagecomponentregisterimpl.h"
#include <vespa/vespalib/util/exceptions.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".storage.component.register");

namespace storage {

StorageComponentRegisterImpl::StorageComponentRegisterImpl()
    : _componentLock(),
      _components(),
      _clusterName(),
      _nodeType(nullptr),
      _index(0xffff),
      _docTypeRepo(),
      _bucketIdFactory(),
      _distribution(),
      _nodeStateUpdater(nullptr),
      _bucketSpacesConfig()
{
}

StorageComponentRegisterImpl::~StorageComponentRegisterImpl() = default;

void
StorageComponentRegisterImpl::registerStorageComponent(StorageComponent& smc)
{
    std::lock_guard lock(_componentLock);
    _components.push_back(&smc);
    assert(_nodeType != nullptr);
    smc.setNodeInfo(_clusterName, *_nodeType, _index);
    if (_nodeStateUpdater != nullptr) {
        smc.setNodeStateUpdater(*_nodeStateUpdater);
    }
    smc.setDocumentTypeRepo(_docTypeRepo);
    smc.setBucketIdFactory(_bucketIdFactory);
    smc.setDistribution(_distribution);
}

void
StorageComponentRegisterImpl::setNodeInfo(vespalib::stringref clusterName,
                                          const lib::NodeType& nodeType,
                                          uint16_t index)
{
    std::lock_guard lock(_componentLock);
    if (_nodeType != nullptr) {
        LOG(warning, "Node info already set. May be valid in tests, but is a "
                     "bug in production. Node info should not be updated live");
    }
    _clusterName = clusterName;
    _nodeType = &nodeType;
    _index = index;
}

void
StorageComponentRegisterImpl::setNodeStateUpdater(NodeStateUpdater& updater)
{
    std::lock_guard lock(_componentLock);
    if (_nodeStateUpdater != 0) {
        throw vespalib::IllegalStateException(
                "Node state updater already set. Should never be altered live.",
                VESPA_STRLOC);
    }
    _nodeStateUpdater = &updater;
    for (auto& component : _components) {
        component->setNodeStateUpdater(updater);
    }
}

void
StorageComponentRegisterImpl::setDocumentTypeRepo(std::shared_ptr<const document::DocumentTypeRepo> repo)
{
    std::lock_guard lock(_componentLock);
    _docTypeRepo = repo;
    for (auto& component : _components) {
        component->setDocumentTypeRepo(repo);
    }
}

void
StorageComponentRegisterImpl::setBucketIdFactory(const document::BucketIdFactory& factory)
{
    std::lock_guard lock(_componentLock);
    _bucketIdFactory = factory;
    for (auto& component : _components) {
        component->setBucketIdFactory(factory);
    }
}

void
StorageComponentRegisterImpl::setDistribution(std::shared_ptr<lib::Distribution> distribution)
{
    std::lock_guard lock(_componentLock);
    _distribution = distribution;
    for (auto& component : _components) {
        component->setDistribution(distribution);
    }
}

void
StorageComponentRegisterImpl::setBucketSpacesConfig(const BucketspacesConfig& config)
{
    std::lock_guard lock(_componentLock);
    _bucketSpacesConfig = config;
}

} // storage
