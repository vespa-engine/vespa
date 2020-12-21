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

#include "cluster_context.h"
#include <vespa/storageframework/generic/component/component.h>
#include <vespa/storageframework/generic/component/componentregister.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/vdslib/state/node.h>
#include <mutex>

namespace document {
    class DocumentTypeRepo;
    class FieldSetRepo;
}

namespace storage {

namespace lib {
    class Distribution;
}
struct NodeStateUpdater;
struct StorageComponentRegister;

class StorageComponent : public framework::Component
{
public:
    struct Repos {
        explicit Repos(std::shared_ptr<const document::DocumentTypeRepo> repo);
        ~Repos();
        const std::shared_ptr<const document::DocumentTypeRepo> documentTypeRepo;
        const std::shared_ptr<const document::FieldSetRepo> fieldSetRepo;
    };
    using UP = std::unique_ptr<StorageComponent>;
    using DistributionSP = std::shared_ptr<lib::Distribution>;

    /**
     * Node type is supposed to be set immediately, and never be updated.
     * Thus it does not need to be threadsafe. Should never be used before set.
     */
    void setNodeInfo(vespalib::stringref clusterName, const lib::NodeType& nodeType, uint16_t index);

    /**
     * Node state updater is supposed to be set immediately, and never be
     * updated. Thus it does not need to be threadsafe. Should never be used
     * before set.
     */
    void setNodeStateUpdater(NodeStateUpdater& updater);
    void setDocumentTypeRepo(std::shared_ptr<const document::DocumentTypeRepo>);
    void setBucketIdFactory(const document::BucketIdFactory&);
    void setDistribution(DistributionSP);

    StorageComponent(StorageComponentRegister&, vespalib::stringref name);
    ~StorageComponent() override;

    const ClusterContext & cluster_context() const noexcept { return _cluster_ctx; }
    const lib::NodeType& getNodeType() const { return *_nodeType; }
    uint16_t getIndex() const { return _index; }
    lib::Node getNode() const { return lib::Node(*_nodeType, _index); }

    vespalib::string getIdentity() const;

    std::shared_ptr<Repos> getTypeRepo() const;
    const document::BucketIdFactory& getBucketIdFactory() const { return _bucketIdFactory; }
    DistributionSP getDistribution() const;
    NodeStateUpdater& getStateUpdater() const;
    uint64_t getGeneration() const { return _generation.load(std::memory_order_relaxed); }
private:
    SimpleClusterContext _cluster_ctx;
    const lib::NodeType* _nodeType;
    uint16_t _index;
    std::shared_ptr<Repos> _repos;
    // TODO: move _distribution in to _repos so lock will only taken once and only copying one shared_ptr.
    document::BucketIdFactory _bucketIdFactory;
    DistributionSP _distribution;
    NodeStateUpdater* _nodeStateUpdater;
    mutable std::mutex _lock;
    std::atomic<uint64_t> _generation;
};

struct StorageComponentRegister : public virtual framework::ComponentRegister
{
    virtual void registerStorageComponent(StorageComponent&) = 0;
};

} // storage
