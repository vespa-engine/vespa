// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storagecomponent.h"
#include <vespa/storage/storageserver/prioritymapper.h>

#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vdslib/distribution/distribution.h>

namespace storage {

// Defined in cpp file to allow unique pointers of unknown type in header.
StorageComponent::~StorageComponent()
{
}

void
StorageComponent::setNodeInfo(vespalib::stringref clusterName,
                              const lib::NodeType& nodeType,
                              uint16_t index)
{
    // Assumed to not be set dynamically.
    _clusterName = clusterName;
    _nodeType = &nodeType;
    _index = index;
}

void
StorageComponent::setDocumentTypeRepo(DocumentTypeRepoSP repo)
{
    std::lock_guard guard(_lock);
    _docTypeRepo = repo;
}

void
StorageComponent::setLoadTypes(LoadTypeSetSP loadTypes)
{
    std::lock_guard guard(_lock);
    _loadTypes = loadTypes;
}


void
StorageComponent::setPriorityConfig(const PriorityConfig& c)
{
    // Priority mapper is already thread safe.
    _priorityMapper->setConfig(c);
}

void
StorageComponent::setBucketIdFactory(const document::BucketIdFactory& factory)
{
    // Assumed to not be set dynamically.
    _bucketIdFactory = factory;
}

void
StorageComponent::setDistribution(DistributionSP distribution)
{
    std::lock_guard guard(_lock);
    _distribution = distribution;
}

void
StorageComponent::setNodeStateUpdater(NodeStateUpdater& updater)
{
    std::lock_guard guard(_lock);
    if (_nodeStateUpdater != 0) {
        throw vespalib::IllegalStateException(
                "Node state updater is already set", VESPA_STRLOC);
    }
    _nodeStateUpdater = &updater;
}

StorageComponent::StorageComponent(StorageComponentRegister& compReg,
                                   vespalib::stringref name)
    : Component(compReg, name),
      _clusterName(),
      _nodeType(nullptr),
      _index(0),
      _docTypeRepo(),
      _loadTypes(),
      _priorityMapper(new PriorityMapper),
      _bucketIdFactory(),
      _distribution(),
      _nodeStateUpdater(nullptr),
      _lock()
{
    compReg.registerStorageComponent(*this);
}

NodeStateUpdater&
StorageComponent::getStateUpdater() const
{
    std::lock_guard guard(_lock);
    if (_nodeStateUpdater == 0) {
        throw vespalib::IllegalStateException(
                "Component need node state updater at this time, but it has "
                "not been initialized.", VESPA_STRLOC);
   }
    return *_nodeStateUpdater;
}

vespalib::string
StorageComponent::getIdentity() const
{
    vespalib::asciistream name;
    name << "storage/cluster." << _clusterName << "/"
         << _nodeType->serialize() << "/" << _index;
    return name.str();
}

uint8_t
StorageComponent::getPriority(const documentapi::LoadType& lt) const
{
    return _priorityMapper->getPriority(lt);
}

StorageComponent::DocumentTypeRepoSP
StorageComponent::getTypeRepo() const
{
    std::lock_guard guard(_lock);
    return _docTypeRepo;
}

StorageComponent::LoadTypeSetSP
StorageComponent::getLoadTypes() const
{
    std::lock_guard guard(_lock);
    return _loadTypes;
}

StorageComponent::DistributionSP
StorageComponent::getDistribution() const
{
    std::lock_guard guard(_lock);
    return _distribution;
}

} // storage
