// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::StorageComponentRegisterImpl
 * \ingroup component
 *
 * \brief Subclass of component register impl that handles storage components.
 */
#pragma once

#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/config-bucketspaces.h>
#include <vespa/storage/config/config-stor-prioritymapping.h>
#include <vespa/storageframework/defaultimplementation/component/componentregisterimpl.h>
#include <vespa/vdslib/distribution/distribution.h>

namespace storage {

class StorageComponentRegisterImpl
        : public virtual StorageComponentRegister,
          public virtual framework::defaultimplementation::ComponentRegisterImpl
{
    using PriorityConfig = StorageComponent::PriorityConfig;
    using BucketspacesConfig = vespa::config::content::core::internal::InternalBucketspacesType;

    vespalib::Lock _componentLock;
    std::vector<StorageComponent*> _components;
    vespalib::string _clusterName;
    const lib::NodeType* _nodeType;
    uint16_t _index;
    std::shared_ptr<const document::DocumentTypeRepo> _docTypeRepo;
    documentapi::LoadTypeSet::SP _loadTypes;
    PriorityConfig _priorityConfig;
    document::BucketIdFactory _bucketIdFactory;
    lib::Distribution::SP _distribution;
    NodeStateUpdater* _nodeStateUpdater;
    BucketspacesConfig _bucketSpacesConfig;
    bool _enableMultipleBucketSpaces;

public:
    typedef std::unique_ptr<StorageComponentRegisterImpl> UP;

    StorageComponentRegisterImpl();
    ~StorageComponentRegisterImpl();

    const vespalib::string& getClusterName() const { return _clusterName; }
    const lib::NodeType& getNodeType() const
        { assert(_nodeType != 0); return *_nodeType; }
    uint16_t getIndex() const { return _index; }
    std::shared_ptr<const document::DocumentTypeRepo> getTypeRepo() { return _docTypeRepo; }
    documentapi::LoadTypeSet::SP getLoadTypes() { return _loadTypes; }
    const document::BucketIdFactory& getBucketIdFactory() { return _bucketIdFactory; }
    lib::Distribution::SP getDistribution() { return _distribution; }
    NodeStateUpdater& getNodeStateUpdater()
        { assert(_nodeStateUpdater != 0); return *_nodeStateUpdater; }

    void registerStorageComponent(StorageComponent&) override;

    void setNodeInfo(vespalib::stringref clusterName,
                     const lib::NodeType& nodeType,
                     uint16_t index);
    virtual void setNodeStateUpdater(NodeStateUpdater& updater);
    virtual void setDocumentTypeRepo(std::shared_ptr<const document::DocumentTypeRepo>);
    virtual void setLoadTypes(documentapi::LoadTypeSet::SP);
    virtual void setPriorityConfig(const PriorityConfig&);
    virtual void setBucketIdFactory(const document::BucketIdFactory&);
    virtual void setDistribution(lib::Distribution::SP);
    virtual void setBucketSpacesConfig(const BucketspacesConfig&);

    virtual void setEnableMultipleBucketSpaces(bool enabled); // To be called during startup configuration phase only.
    bool enableMultipleBucketSpaces() const;

};

} // storage
