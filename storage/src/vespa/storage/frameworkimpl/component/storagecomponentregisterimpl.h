// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::StorageComponentRegisterImpl
 * \ingroup component
 *
 * \brief Subclass of component register impl that handles storage components.
 */
#pragma once

#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/config-bucketspaces.h>
#include <vespa/storageframework/defaultimplementation/component/componentregisterimpl.h>

namespace storage::lib { class Distribution; }
namespace storage {

class StorageComponentRegisterImpl
        : public virtual StorageComponentRegister,
          public virtual framework::defaultimplementation::ComponentRegisterImpl
{
    using BucketspacesConfig = vespa::config::content::core::internal::InternalBucketspacesType;

    std::mutex _componentLock;
    std::vector<StorageComponent*> _components;
    vespalib::string _clusterName;
    const lib::NodeType* _nodeType;
    uint16_t _index;
    std::shared_ptr<const document::DocumentTypeRepo> _docTypeRepo;
    document::BucketIdFactory _bucketIdFactory;
    std::shared_ptr<lib::Distribution> _distribution;
    NodeStateUpdater* _nodeStateUpdater;
    BucketspacesConfig _bucketSpacesConfig;

public:
    typedef std::unique_ptr<StorageComponentRegisterImpl> UP;

    StorageComponentRegisterImpl();
    ~StorageComponentRegisterImpl() override;

    const lib::NodeType& getNodeType() const { return *_nodeType; }
    uint16_t getIndex() const { return _index; }
    std::shared_ptr<const document::DocumentTypeRepo> getTypeRepo() { return _docTypeRepo; }
    const document::BucketIdFactory& getBucketIdFactory() { return _bucketIdFactory; }
    std::shared_ptr<lib::Distribution> & getDistribution() { return _distribution; }
    NodeStateUpdater& getNodeStateUpdater() { return *_nodeStateUpdater; }

    void registerStorageComponent(StorageComponent&) override;

    void setNodeInfo(vespalib::stringref clusterName, const lib::NodeType& nodeType, uint16_t index);
    virtual void setNodeStateUpdater(NodeStateUpdater& updater);
    virtual void setDocumentTypeRepo(std::shared_ptr<const document::DocumentTypeRepo>);
    virtual void setBucketIdFactory(const document::BucketIdFactory&);
    virtual void setDistribution(std::shared_ptr<lib::Distribution>);
    virtual void setBucketSpacesConfig(const BucketspacesConfig&);
};

} // storage
