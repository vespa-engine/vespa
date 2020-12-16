// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storagecomponent.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/document/fieldset/fieldsetrepo.h>
#include <cassert>

namespace storage {

StorageComponent::Repos::Repos(std::shared_ptr<const document::DocumentTypeRepo> repo)
    : documentTypeRepo(std::move(repo)),
      fieldSetRepo(std::make_shared<document::FieldSetRepo>(*documentTypeRepo))
{}

StorageComponent::Repos::~Repos() = default;

// Defined in cpp file to allow unique pointers of unknown type in header.
StorageComponent::~StorageComponent() = default;

void
StorageComponent::setNodeInfo(vespalib::stringref clusterName,
                              const lib::NodeType& nodeType,
                              uint16_t index)
{
    // Assumed to not be set dynamically.
    assert(_cluster_ctx.my_cluster_name.empty());
    _cluster_ctx.my_cluster_name = clusterName;
    _nodeType = &nodeType;
    _index = index;
}

void
StorageComponent::setDocumentTypeRepo(std::shared_ptr<const document::DocumentTypeRepo> docTypeRepo)
{
    auto repo = std::make_shared<Repos>(std::move(docTypeRepo));
    std::lock_guard guard(_lock);
    _repos = std::move(repo);
    _generation++;
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
    _generation++;
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
      _cluster_ctx(),
      _nodeType(nullptr),
      _index(0),
      _repos(),
      _bucketIdFactory(),
      _distribution(),
      _nodeStateUpdater(nullptr),
      _lock(),
      _generation(0)
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
    name << "storage/cluster." << _cluster_ctx.cluster_name() << "/"
         << _nodeType->serialize() << "/" << _index;
    return name.str();
}

std::shared_ptr<StorageComponent::Repos>
StorageComponent::getTypeRepo() const
{
    std::lock_guard guard(_lock);
    return _repos;
}

StorageComponent::DistributionSP
StorageComponent::getDistribution() const
{
    std::lock_guard guard(_lock);
    return _distribution;
}

} // storage
