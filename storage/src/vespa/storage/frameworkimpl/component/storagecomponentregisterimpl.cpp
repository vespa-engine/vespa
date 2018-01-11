// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storagecomponentregisterimpl.h"
#include <vespa/vespalib/util/exceptions.h>

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
      _loadTypes(new documentapi::LoadTypeSet),
      _priorityConfig(),
      _bucketIdFactory(),
      _distribution(),
      _nodeStateUpdater(nullptr),
      _bucketSpacesConfig(),
      _enableMultipleBucketSpaces(false)
{
}

StorageComponentRegisterImpl::~StorageComponentRegisterImpl() { }

void
StorageComponentRegisterImpl::registerStorageComponent(StorageComponent& smc)
{
    vespalib::LockGuard lock(_componentLock);
    _components.push_back(&smc);
    assert(_nodeType != nullptr);
    smc.setNodeInfo(_clusterName, *_nodeType, _index);
    if (_nodeStateUpdater != nullptr) {
        smc.setNodeStateUpdater(*_nodeStateUpdater);
    }
    smc.setDocumentTypeRepo(_docTypeRepo);
    smc.setLoadTypes(_loadTypes);
    smc.setPriorityConfig(_priorityConfig);
    smc.setBucketIdFactory(_bucketIdFactory);
    smc.setDistribution(_distribution);
    smc.enableMultipleBucketSpaces(_enableMultipleBucketSpaces);
}

void
StorageComponentRegisterImpl::setNodeInfo(vespalib::stringref clusterName,
                                          const lib::NodeType& nodeType,
                                          uint16_t index)
{
    vespalib::LockGuard lock(_componentLock);
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
    vespalib::LockGuard lock(_componentLock);
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
StorageComponentRegisterImpl::setDocumentTypeRepo(document::DocumentTypeRepo::SP repo)
{
    vespalib::LockGuard lock(_componentLock);
    _docTypeRepo = repo;
    for (auto& component : _components) {
        component->setDocumentTypeRepo(repo);
    }
}

void
StorageComponentRegisterImpl::setLoadTypes(documentapi::LoadTypeSet::SP loadTypes)
{
    vespalib::LockGuard lock(_componentLock);
    _loadTypes = loadTypes;
    for (auto& component : _components) {
        component->setLoadTypes(loadTypes);
    }
}

void
StorageComponentRegisterImpl::setPriorityConfig(const PriorityConfig& config)
{
    vespalib::LockGuard lock(_componentLock);
    _priorityConfig = config;
    for (auto& component : _components) {
        component->setPriorityConfig(config);
    }
}

void
StorageComponentRegisterImpl::setBucketIdFactory(const document::BucketIdFactory& factory)
{
    vespalib::LockGuard lock(_componentLock);
    _bucketIdFactory = factory;
    for (auto& component : _components) {
        component->setBucketIdFactory(factory);
    }
}

void
StorageComponentRegisterImpl::setDistribution(lib::Distribution::SP distribution)
{
    vespalib::LockGuard lock(_componentLock);
    _distribution = distribution;
    for (auto& component : _components) {
        component->setDistribution(distribution);
    }
}

void
StorageComponentRegisterImpl::setBucketSpacesConfig(const BucketspacesConfig& config)
{
    vespalib::LockGuard lock(_componentLock);
    _bucketSpacesConfig = config;
}

void StorageComponentRegisterImpl::setEnableMultipleBucketSpaces(bool enabled) {
    vespalib::LockGuard lock(_componentLock);
    assert(!_enableMultipleBucketSpaces); // Cannot disable once enabled.
    _enableMultipleBucketSpaces = enabled;
    for (auto& component : _components) {
        component->enableMultipleBucketSpaces(_enableMultipleBucketSpaces);
    }
}

bool StorageComponentRegisterImpl::enableMultipleBucketSpaces() const {
    // We allow reading this outside _componentLock, as it should never be written
    // again after startup.
    return _enableMultipleBucketSpaces;
}

} // storage
