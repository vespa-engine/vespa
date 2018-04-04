// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::StorageComponent
 * \ingroup common
 *
 * \brief Component class including some storage specific information.
 *
 * The storage framework defines components with generic functionality.
 * The storage component inherits from this and adds some storage specific
 * components. Further, the distributor component and service layer component
 * will inherit from this to also include distributor and service layer specific
 * implementations.
 */

/**
 * \class storage::StorageComponentRegister
 * \ingroup common
 *
 * \brief Specialization of ComponentRegister handling storage components.
 */

/**
 * \class storage::StorageManagedComponent
 * \ingroup common
 *
 * \brief Specialization of ManagedComponent to set storage functionality.
 *
 * A storage component register will use this interface in order to set the
 * storage functionality parts.
 */

#pragma once

#include <vespa/storageframework/generic/component/component.h>
#include <vespa/storageframework/generic/component/componentregister.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/vdslib/state/node.h>
#include <mutex>

namespace vespa::config::content::core::internal {
    class InternalStorPrioritymappingType;
}
namespace document {
    class DocumentTypeRepo;
}
namespace documentapi {
    class LoadType;
    class LoadTypeSet;
}

namespace storage {
namespace lib {
    class Distribution;
}
class NodeStateUpdater;
class PriorityMapper;
class StorageComponentRegister;

class StorageComponent : public framework::Component {
public:
    using UP = std::unique_ptr<StorageComponent>;
    using PriorityConfig = vespa::config::content::core::internal::InternalStorPrioritymappingType;
    using DocumentTypeRepoSP = std::shared_ptr<const document::DocumentTypeRepo>;
    using LoadTypeSetSP = std::shared_ptr<documentapi::LoadTypeSet>;
    using DistributionSP = std::shared_ptr<lib::Distribution>;

    /**
     * Node type is supposed to be set immediately, and never be updated.
     * Thus it does not need to be threadsafe. Should never be used before set.
     */
    void setNodeInfo(vespalib::stringref clusterName,
                     const lib::NodeType& nodeType,
                     uint16_t index);

    /**
     * Node state updater is supposed to be set immediately, and never be
     * updated. Thus it does not need to be threadsafe. Should never be used
     * before set.
     */
    void setNodeStateUpdater(NodeStateUpdater& updater);
    void setDocumentTypeRepo(DocumentTypeRepoSP);
    void setLoadTypes(LoadTypeSetSP);
    void setPriorityConfig(const PriorityConfig&);
    void setBucketIdFactory(const document::BucketIdFactory&);
    void setDistribution(DistributionSP);
    void enableMultipleBucketSpaces(bool value);

    StorageComponent(StorageComponentRegister&, vespalib::stringref name);
    virtual ~StorageComponent();

    vespalib::string getClusterName() const { return _clusterName; }
    const lib::NodeType& getNodeType() const { return *_nodeType; }
    uint16_t getIndex() const { return _index; }
    lib::Node getNode() const { return lib::Node(*_nodeType, _index); }

    vespalib::string getIdentity() const;

    DocumentTypeRepoSP getTypeRepo() const;
    LoadTypeSetSP getLoadTypes() const;
    const document::BucketIdFactory& getBucketIdFactory() const
        { return _bucketIdFactory; }
    uint8_t getPriority(const documentapi::LoadType&) const;
    DistributionSP getDistribution() const;
    NodeStateUpdater& getStateUpdater() const;
    bool enableMultipleBucketSpaces() const;

private:
    vespalib::string _clusterName;
    const lib::NodeType* _nodeType;
    uint16_t _index;
    DocumentTypeRepoSP _docTypeRepo;
    LoadTypeSetSP _loadTypes;
    std::unique_ptr<PriorityMapper> _priorityMapper;
    document::BucketIdFactory _bucketIdFactory;
    DistributionSP _distribution;
    NodeStateUpdater* _nodeStateUpdater;
    mutable std::mutex _lock;
    bool _enableMultipleBucketSpaces;
};

struct StorageComponentRegister : public virtual framework::ComponentRegister
{
    virtual void registerStorageComponent(StorageComponent&) = 0;
};

} // storage
