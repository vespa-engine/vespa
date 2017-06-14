// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storagecomponentregisterimpl.h"
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>

LOG_SETUP(".storage.component.register");

namespace storage {

StorageComponentRegisterImpl::StorageComponentRegisterImpl()
    : _nodeType(0),
      _index(0xffff),
      _loadTypes(new documentapi::LoadTypeSet),
      _nodeStateUpdater(0)
{ }

StorageComponentRegisterImpl::~StorageComponentRegisterImpl() { }

void
StorageComponentRegisterImpl::registerStorageComponent(StorageComponent& smc)
{
    vespalib::LockGuard lock(_componentLock);
    _components.push_back(&smc);
    assert(_nodeType != 0);
    smc.setNodeInfo(_clusterName, *_nodeType, _index);
    if (_nodeStateUpdater != 0) {
        smc.setNodeStateUpdater(*_nodeStateUpdater);
    }
    smc.setDocumentTypeRepo(_docTypeRepo);
    smc.setLoadTypes(_loadTypes);
    smc.setPriorityConfig(_priorityConfig);
    smc.setBucketIdFactory(_bucketIdFactory);
    smc.setDistribution(_distribution);
}

void
StorageComponentRegisterImpl::setNodeInfo(vespalib::stringref clusterName,
                                          const lib::NodeType& nodeType,
                                          uint16_t index)
{
    vespalib::LockGuard lock(_componentLock);
    if (_nodeType != 0) {
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
    for (uint32_t i=0; i<_components.size(); ++i) {
        _components[i]->setNodeStateUpdater(updater);
    }
}

void
StorageComponentRegisterImpl::setDocumentTypeRepo(document::DocumentTypeRepo::SP repo)
{
    vespalib::LockGuard lock(_componentLock);
    _docTypeRepo = repo;
    for (uint32_t i=0; i<_components.size(); ++i) {
        _components[i]->setDocumentTypeRepo(repo);
    }
}

void
StorageComponentRegisterImpl::setLoadTypes(documentapi::LoadTypeSet::SP loadTypes)
{
    vespalib::LockGuard lock(_componentLock);
    _loadTypes = loadTypes;
    for (uint32_t i=0; i<_components.size(); ++i) {
        _components[i]->setLoadTypes(loadTypes);
    }
}

void
StorageComponentRegisterImpl::setPriorityConfig(const PriorityConfig& config)
{
    vespalib::LockGuard lock(_componentLock);
    _priorityConfig = config;
    for (uint32_t i=0; i<_components.size(); ++i) {
        _components[i]->setPriorityConfig(config);
    }
}

void
StorageComponentRegisterImpl::setBucketIdFactory(const document::BucketIdFactory& factory)
{
    vespalib::LockGuard lock(_componentLock);
    _bucketIdFactory = factory;
    for (uint32_t i=0; i<_components.size(); ++i) {
        _components[i]->setBucketIdFactory(factory);
    }
}

void
StorageComponentRegisterImpl::setDistribution(lib::Distribution::SP distribution)
{
    vespalib::LockGuard lock(_componentLock);
    _distribution = distribution;
    for (uint32_t i=0; i<_components.size(); ++i) {
        _components[i]->setDistribution(distribution);
    }
}

} // storage
